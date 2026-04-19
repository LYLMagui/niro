create temp table if not exists tmp_cs2_goods_raw (
  dataset varchar(64) not null,
  payload jsonb not null
);

truncate table tmp_cs2_goods_raw;

insert into tmp_cs2_goods_raw (dataset, payload)
select 'skins_not_grouped', value
from jsonb_array_elements(pg_read_file('/tmp/niro-cs2-seed/skins_not_grouped.json')::jsonb) as value;

insert into tmp_cs2_goods_raw (dataset, payload)
select 'crates', value
from jsonb_array_elements(pg_read_file('/tmp/niro-cs2-seed/crates.json')::jsonb) as value;

insert into tmp_cs2_goods_raw (dataset, payload)
select 'keys', value
from jsonb_array_elements(pg_read_file('/tmp/niro-cs2-seed/keys.json')::jsonb) as value;

insert into tmp_cs2_goods_raw (dataset, payload)
select 'stickers', value
from jsonb_array_elements(pg_read_file('/tmp/niro-cs2-seed/stickers.json')::jsonb) as value;

insert into tmp_cs2_goods_raw (dataset, payload)
select 'agents', value
from jsonb_array_elements(pg_read_file('/tmp/niro-cs2-seed/agents.json')::jsonb) as value;

insert into tmp_cs2_goods_raw (dataset, payload)
select 'patches', value
from jsonb_array_elements(pg_read_file('/tmp/niro-cs2-seed/patches.json')::jsonb) as value;

insert into tmp_cs2_goods_raw (dataset, payload)
select 'music_kits', value
from jsonb_array_elements(pg_read_file('/tmp/niro-cs2-seed/music_kits.json')::jsonb) as value;

insert into tmp_cs2_goods_raw (dataset, payload)
select 'graffiti', value
from jsonb_array_elements(pg_read_file('/tmp/niro-cs2-seed/graffiti.json')::jsonb) as value;

create temp table tmp_cs2_goods_stage as
with normalized as (
  select
    dataset,
    payload,
    coalesce(nullif(payload->>'id', ''), nullif(payload->>'def_index', ''), md5(payload::text)) as source_id,
    coalesce(nullif(payload->>'name', ''), nullif(payload->>'market_hash_name', ''), nullif(payload->>'id', ''), nullif(payload->>'def_index', '')) as source_name,
    coalesce(nullif(payload->>'market_hash_name', ''), nullif(payload->>'name', '')) as market_hash_name,
    coalesce(nullif(payload->>'name', ''), nullif(payload->>'market_hash_name', '')) as display_name,
    case
      when dataset = 'skins_not_grouped' then regexp_replace(
        coalesce(nullif(payload->>'market_hash_name', ''), nullif(payload->>'name', '')),
        ' \((Factory New|Minimal Wear|Field-Tested|Well-Worn|Battle-Scarred)\)$',
        ''
      )
      else coalesce(nullif(payload->>'market_hash_name', ''), nullif(payload->>'name', ''))
    end as base_name,
    coalesce(nullif(payload->>'id', ''), nullif(payload->>'def_index', ''), md5(payload::text)) as internal_name,
    case
      when dataset = 'skins_not_grouped' then
        case
          when coalesce(payload #>> '{category,id}', '') = 'sfui_invpanel_filter_melee' then 'knife'
          when coalesce(payload #>> '{category,id}', '') = 'sfui_invpanel_filter_gloves' then 'glove'
          else 'weapon_skin'
        end
      when dataset = 'crates' then 'case'
      when dataset = 'keys' then 'key'
      when dataset = 'stickers' then 'sticker'
      when dataset = 'agents' then 'agent'
      when dataset = 'patches' then 'patch'
      when dataset = 'music_kits' then 'music_kit'
      when dataset = 'graffiti' then 'graffiti'
      else dataset
    end as item_type,
    case
      when dataset = 'skins_not_grouped' then lower(regexp_replace(coalesce(payload #>> '{category,id}', ''), '^sfui_invpanel_filter_', ''))
      else ''
    end as weapon_type,
    lower(regexp_replace(coalesce(payload #>> '{rarity,id}', ''), '^rarity_', '')) as rarity,
    case
      when dataset = 'skins_not_grouped' and coalesce(payload #>> '{wear,id}', '') <> '' then coalesce((substring(payload #>> '{wear,id}' from '.*_(\d+)$'))::integer, -1)
      else -1
    end as exterior_code,
    case
      when dataset = 'skins_not_grouped' then coalesce(payload #>> '{wear,name}', '')
      else ''
    end as exterior_name,
    case
      when dataset = 'skins_not_grouped' then coalesce(payload #>> '{wear,id}', '') <> ''
      else false
    end as has_exterior,
    coalesce((payload->>'stattrak')::boolean, false) as stattrak,
    coalesce((payload->>'souvenir')::boolean, false) as souvenir,
    case
      when dataset = 'skins_not_grouped' then coalesce((payload->>'min_float')::numeric, 0)
      else 0
    end as min_wear,
    case
      when dataset = 'skins_not_grouped' then coalesce((payload->>'max_float')::numeric, 0)
      else 0
    end as max_wear,
    coalesce(nullif(payload->>'image', ''), '') as image_url,
    coalesce(nullif(payload->>'image', ''), '') as original_image_url,
    payload as source_payload
  from tmp_cs2_goods_raw
), deduped as (
  select distinct on (market_hash_name)
    dataset,
    source_id,
    source_name,
    market_hash_name,
    display_name,
    base_name,
    base_name as short_name,
    internal_name,
    item_type,
    weapon_type,
    rarity,
    exterior_code,
    exterior_name,
    has_exterior,
    stattrak,
    souvenir,
    min_wear,
    max_wear,
    image_url,
    original_image_url,
    source_payload
  from normalized
  where coalesce(market_hash_name, '') <> ''
  order by market_hash_name, dataset
)
select * from deduped;

insert into public.cs2_goods (
  market_hash_name,
  display_name,
  base_name,
  short_name,
  internal_name,
  item_type,
  weapon_type,
  rarity,
  exterior_code,
  exterior_name,
  has_exterior,
  stattrak,
  souvenir,
  min_wear,
  max_wear,
  image_url,
  original_image_url,
  source_payload,
  enabled,
  created_at,
  updated_at
)
select
  market_hash_name,
  display_name,
  base_name,
  short_name,
  internal_name,
  item_type,
  weapon_type,
  rarity,
  exterior_code,
  exterior_name,
  has_exterior,
  stattrak,
  souvenir,
  min_wear,
  max_wear,
  image_url,
  original_image_url,
  source_payload,
  true,
  now(),
  now()
from tmp_cs2_goods_stage
on conflict (market_hash_name) do update set
  display_name = excluded.display_name,
  base_name = excluded.base_name,
  short_name = excluded.short_name,
  internal_name = excluded.internal_name,
  item_type = excluded.item_type,
  weapon_type = excluded.weapon_type,
  rarity = excluded.rarity,
  exterior_code = excluded.exterior_code,
  exterior_name = excluded.exterior_name,
  has_exterior = excluded.has_exterior,
  stattrak = excluded.stattrak,
  souvenir = excluded.souvenir,
  min_wear = excluded.min_wear,
  max_wear = excluded.max_wear,
  image_url = excluded.image_url,
  original_image_url = excluded.original_image_url,
  source_payload = excluded.source_payload,
  enabled = excluded.enabled,
  updated_at = now();

insert into public.cs2_goods_source_map (
  goods_id,
  source_type,
  source_id,
  source_name,
  extra,
  created_at,
  updated_at
)
select
  goods.id,
  'bymykel',
  stage.source_id,
  stage.source_name,
  jsonb_build_object(
    'dataset', stage.dataset,
    'market_hash_name', stage.market_hash_name,
    'display_name', stage.display_name,
    'original', stage.source_payload->'original'
  ),
  now(),
  now()
from tmp_cs2_goods_stage stage
join public.cs2_goods goods on goods.market_hash_name = stage.market_hash_name
on conflict (source_type, source_id) do update set
  goods_id = excluded.goods_id,
  source_name = excluded.source_name,
  extra = excluded.extra,
  updated_at = now();
