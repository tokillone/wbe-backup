import { readFile, writeFile } from 'node:fs/promises'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const SCRIPT_DIR = dirname(fileURLToPath(import.meta.url))
const WORKSPACE_DIR = resolve(SCRIPT_DIR, '..', '..')
const INPUT_PATHS = [
  resolve(WORKSPACE_DIR, 'wbe-ui/public/geo/world-admin1.geojson'),
  resolve(WORKSPACE_DIR, 'wbe-ui/public/geo/china-provinces.geojson'),
]
const OUTPUT_PATH = resolve(
  WORKSPACE_DIR,
  'wbe-backup/src/main/resources/db/map_admin1_geolocation_seed.sql',
)
const SEED_VERSION = 20260810

// Only unambiguous aliases found in the imported workbook are listed here.
// Broader regions such as Japan/Kanto and UK/England intentionally remain unassigned.
const ALIAS_OVERRIDES = {
  australia: {
    nt: 'northernterritory',
    qld: 'queensland',
    tas: 'tasmania',
    vic: 'victoria',
    wa: 'westernaustralia',
  },
  belgium: {
    oostvlaanderen: 'eastflanders',
  },
  china: {
    hongkongsar: 'hongkong',
    innermongolia: 'neimenggu',
    tianjing: 'tianjin',
    tibet: 'xizang',
  },
  switzerland: {
    cantonofzurich: 'zurich',
  },
}

function normalizeAlias(value) {
  return String(value ?? '')
    .toLowerCase()
    .replace(/[^0-9a-zA-Z]+/g, '')
}

function sqlString(value) {
  if (value == null || value === '') return 'NULL'
  return `'${String(value).replaceAll("'", "''")}'`
}

function coordinateRing(value) {
  if (!Array.isArray(value)) return []
  return value.flatMap((item) =>
    Array.isArray(item) && item.length >= 2 && Number.isFinite(item[0]) && Number.isFinite(item[1])
      ? [[Number(item[0]), Number(item[1])]]
      : [],
  )
}

function polygonArea(ring) {
  let area = 0
  for (let index = 0; index < ring.length; index += 1) {
    const current = ring[index]
    const next = ring[(index + 1) % ring.length]
    area += current[0] * next[1] - next[0] * current[1]
  }
  return Math.abs(area / 2)
}

function primaryRing(geometry) {
  if (geometry?.type === 'Polygon') return coordinateRing(geometry.coordinates?.[0])
  if (geometry?.type !== 'MultiPolygon') return []
  return geometry.coordinates
    .map((polygon) => coordinateRing(polygon?.[0]))
    .filter((ring) => ring.length >= 3)
    .sort((left, right) => polygonArea(right) - polygonArea(left))[0] ?? []
}

function centroid(ring) {
  if (ring.length < 3) return [null, null]
  let twiceArea = 0
  let longitude = 0
  let latitude = 0
  for (let index = 0; index < ring.length; index += 1) {
    const current = ring[index]
    const next = ring[(index + 1) % ring.length]
    const cross = current[0] * next[1] - next[0] * current[1]
    twiceArea += cross
    longitude += (current[0] + next[0]) * cross
    latitude += (current[1] + next[1]) * cross
  }
  if (Math.abs(twiceArea) < 1e-9) {
    const longitudes = ring.map((point) => point[0])
    const latitudes = ring.map((point) => point[1])
    return [
      (Math.min(...longitudes) + Math.max(...longitudes)) / 2,
      (Math.min(...latitudes) + Math.max(...latitudes)) / 2,
    ]
  }
  return [longitude / (3 * twiceArea), latitude / (3 * twiceArea)]
}

function featureAliases(properties) {
  const countryKey = normalizeAlias(properties.country_key)
  const values = [properties.region_key, properties.display_name, properties.name]
  for (const key of Array.isArray(properties.keys) ? properties.keys : []) {
    values.push(key, ...String(key).split('|'))
  }
  const aliases = new Set()
  for (const value of values) {
    const alias = normalizeAlias(value)
    if (!alias || alias === countryKey) continue
    aliases.add(alias)
  }
  return aliases
}

const boundaries = await Promise.all(
  INPUT_PATHS.map(async (path) => JSON.parse(await readFile(path, 'utf8'))),
)
const locations = new Map()
const aliasCandidates = new Map()

for (const feature of boundaries.flatMap((boundary) => boundary.features ?? [])) {
  const properties = feature.properties ?? {}
  const countryKey = normalizeAlias(properties.country_key)
  const regionKey = normalizeAlias(properties.region_key)
  if (!countryKey || !regionKey) continue
  const geoKey = `${countryKey}|${regionKey}`
  const displayName = String(properties.display_name ?? properties.name ?? regionKey)
  const country = String(properties.country_display ?? countryKey)
  const [longitude, latitude] = centroid(primaryRing(feature.geometry))
  locations.set(geoKey, { geoKey, countryKey, country, displayName, latitude, longitude })

  for (const alias of featureAliases(properties)) {
    const scopedAlias = `${countryKey}|${alias}`
    const candidates = aliasCandidates.get(scopedAlias) ?? new Set()
    candidates.add(geoKey)
    aliasCandidates.set(scopedAlias, candidates)
  }
}

for (const [countryKey, overrides] of Object.entries(ALIAS_OVERRIDES)) {
  for (const [alias, regionKey] of Object.entries(overrides)) {
    const geoKey = `${countryKey}|${regionKey}`
    if (!locations.has(geoKey)) {
      throw new Error(`Alias override target does not exist: ${countryKey}|${alias} -> ${geoKey}`)
    }
    aliasCandidates.set(`${countryKey}|${normalizeAlias(alias)}`, new Set([geoKey]))
  }
}

const locationRows = [...locations.values()]
  .sort((left, right) => left.geoKey.localeCompare(right.geoKey))
  .map(
    (item) =>
      `('admin1', ${sqlString(item.geoKey)}, ${sqlString(item.countryKey)}, ${sqlString(item.country)}, ${sqlString(item.displayName)}, NULL, ${sqlString(item.displayName)}, ${item.latitude?.toFixed(7) ?? 'NULL'}, ${item.longitude?.toFixed(7) ?? 'NULL'}, TRUE, 'world-admin1-boundary')`,
  )

const aliasRows = [...aliasCandidates.entries()]
  .flatMap(([scopedAlias, candidates]) => {
    if (candidates.size !== 1) return []
    const separator = scopedAlias.indexOf('|')
    const countryKey = scopedAlias.slice(0, separator)
    const alias = scopedAlias.slice(separator + 1)
    const geoKey = [...candidates][0]
    return [{ countryKey, alias, geoKey }]
  })
  .sort((left, right) =>
    `${left.countryKey}|${left.alias}`.localeCompare(`${right.countryKey}|${right.alias}`),
  )
  .map(
    (item) =>
      `('admin1', ${sqlString(item.countryKey)}, ${sqlString(item.alias)}, ${sqlString(item.geoKey)}, 'world-admin1-boundary', ${SEED_VERSION})`,
  )

const sql = `-- Generated by scripts/generate-map-admin1-seed.mjs. Do not edit by hand.\n\nINSERT INTO geo_locations (level, geo_key, parent_geo_key, country, province, city, display_name, latitude, longitude, is_mappable, coordinate_source) VALUES\n${locationRows.join(',\n')}\nON DUPLICATE KEY UPDATE\n  parent_geo_key = VALUES(parent_geo_key),\n  country = VALUES(country),\n  province = VALUES(province),\n  city = VALUES(city),\n  display_name = VALUES(display_name),\n  latitude = VALUES(latitude),\n  longitude = VALUES(longitude),\n  is_mappable = VALUES(is_mappable),\n  coordinate_source = VALUES(coordinate_source);\n\nDELETE FROM geo_location_aliases WHERE level = 'admin1' AND source = 'world-admin1-boundary';\n\nINSERT INTO geo_location_aliases (level, country_key, alias_key, geo_key, source, source_version) VALUES\n${aliasRows.join(',\n')}\nON DUPLICATE KEY UPDATE\n  geo_key = VALUES(geo_key),\n  source = VALUES(source),\n  source_version = VALUES(source_version);\n`

await writeFile(OUTPUT_PATH, sql)
console.log(`Generated ${locationRows.length} admin1 locations and ${aliasRows.length} aliases.`)
