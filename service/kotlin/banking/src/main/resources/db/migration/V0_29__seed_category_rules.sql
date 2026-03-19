-- V0_29__seed_category_rules.sql
-- Comprehensive system auto-categorization rules derived from:
--   docs/03-features/banking/transaction-enrichment/merchants-seed.csv
--
-- Replaces the sparse V0_26 seed which had two problems:
--   1. Wrong category names ('Aplicativos (Uber)', 'Streaming') that don't exist
--      in the DB → those INSERTs silently inserted 0 rows.
--   2. Priority = 10, which made system rules HIGHER priority than explicit user
--      rules (40) — inverting the intended tier order.
--
-- This migration:
--   - Deletes all existing system seed rules (is_system = true, account_id IS NULL)
--   - Inserts ~500 CONTAINS rules with priority = 100 (correct tier for system rules)
--
-- Pattern design:
--   - CONTAINS: bank/card providers append suffixes, location codes and identifiers
--     e.g. "NETFLIX.COM*BR", "IFOOD*99123 SP", "CARREFOUR MKT 042"
--   - Patterns are pre-normalized (lowercase, no accents, no special chars except spaces)
--     matching the output of CategorizationService.normalize()
--   - Short/ambiguous names are excluded (e.g. "extra", "posto", "tim", "oi", "sky")
--     to avoid false positives. Implicit learning fills those gaps over time.
--
-- Insert pattern used throughout:
--   SELECT NULL, cat.id, p.pattern, 'CONTAINS', 'EXPENSE', 100, true
--   FROM (SELECT id FROM banking.transaction_categories WHERE name = '...' LIMIT 1) AS cat
--   CROSS JOIN (VALUES (...)) AS p(pattern)
--
--   If the category does not exist the subquery returns 0 rows → CROSS JOIN produces
--   0 rows → INSERT inserts nothing (safe for test environments with partial seeds).

SET search_path TO banking, public;

-- ─────────────────────────────────────────────────────────────────────────────
-- Remove sparse V0_26 system seed to avoid duplicates and fix wrong references
-- ─────────────────────────────────────────────────────────────────────────────
DELETE FROM banking.category_rules
WHERE is_system = true AND account_id IS NULL AND is_implicit = false;


-- ─────────────────────────────────────────────────────────────────────────────
-- ALIMENTAÇÃO
-- ─────────────────────────────────────────────────────────────────────────────

-- Supermercado
-- Skipped: 'extra' (matches 'extrafarma' → false positive on pharmacy transactions)
INSERT INTO banking.category_rules (account_id, category_id, pattern, pattern_type, applicable_to, priority, is_system)
SELECT NULL, cat.id, p.pattern, 'CONTAINS', 'EXPENSE', 100, true
FROM (SELECT id FROM banking.transaction_categories WHERE name = 'Supermercado' AND is_system = true LIMIT 1) AS cat
CROSS JOIN (VALUES
  ('carrefour'), ('atacadao'), ('assai'), ('pao de acucar'),
  ('sams club'), ('makro'), ('spani'), ('condor'), ('muffato'),
  ('angeloni'), ('giassi'), ('zaffari'), ('prezunic'), ('zona sul'),
  ('bahamas'), ('bretas'), ('verdemar'), ('bh supermercados'), ('hirota'),
  ('hortifruti'), ('oba hortifruti'), ('natural da terra'), ('saint marche'),
  ('sonda'), ('comper'), ('nacional supermercados')
) AS p(pattern);

-- Delivery
INSERT INTO banking.category_rules (account_id, category_id, pattern, pattern_type, applicable_to, priority, is_system)
SELECT NULL, cat.id, p.pattern, 'CONTAINS', 'EXPENSE', 100, true
FROM (SELECT id FROM banking.transaction_categories WHERE name = 'Delivery' AND is_system = true LIMIT 1) AS cat
CROSS JOIN (VALUES
  ('ifood'), ('rappi'), ('uber eats')
) AS p(pattern);

-- Restaurante
-- Normalized: 'ráscal' → 'rascal', 'fran''s cafe' → 'frans cafe',
--             'cook''n roll' → 'cookn roll', 'd''accorsi' → 'daccorsi',
--             'mc donalds' kept as separate pattern from 'mcdonalds'
INSERT INTO banking.category_rules (account_id, category_id, pattern, pattern_type, applicable_to, priority, is_system)
SELECT NULL, cat.id, p.pattern, 'CONTAINS', 'EXPENSE', 100, true
FROM (SELECT id FROM banking.transaction_categories WHERE name = 'Restaurante' AND is_system = true LIMIT 1) AS cat
CROSS JOIN (VALUES
  ('mcdonalds'), ('mc donalds'), ('burger king'), ('subway'), ('kfc'),
  ('pizza hut'), ('dominos'), ('bobs'), ('giraffas'), ('habibs'),
  ('spoleto'), ('madero'), ('china in box'), ('ragazzo'), ('jeronimo'),
  ('frango assado'), ('outback'), ('starbucks'), ('oakberry'), ('taco bell'),
  ('five guys'), ('popeyes'), ('dunkin'), ('koni'), ('baked potato'),
  ('rei do mate'), ('casa do pao de queijo'), ('frans cafe'), ('blend burguer'),
  ('coco bambu'), ('vivenda do camarao'), ('almanara'), ('fogo de chao'),
  ('pobre juan'), ('gendai'), ('camarada camarao'), ('patroni'), ('parme'),
  ('montana grill'), ('divino fogao'), ('chilis'), ('applebees'),
  ('hard rock cafe'), ('sushiloko'), ('temakeria'), ('hokkai'), ('bibi sucos'),
  ('rascal'), ('ofner'), ('arabia'), ('barbacoa'), ('rubaiyat'),
  ('le pain quotidien'), ('mr cheney'), ('forneria san paolo'), ('yogoberry'),
  ('pitaya'), ('bella paulista'), ('tenda do morango'), ('cookn roll'),
  ('daccorsi'), ('bullguer'), ('lanchonete da cidade'), ('fifties'),
  ('braz pizzaria'), ('familia mancini'), ('viena'), ('amor aos pedacos'),
  ('freddo'), ('bacio di latte'), ('mil frutas'), ('haagen dazs'), ('hooters'),
  ('tgi fridays'), ('pf changs'), ('nagayama'), ('shoji'), ('salad creations'),
  ('copa cafe'), ('quitandinha'), ('cumbuca'), ('santa massa'), ('nonno ruggero'),
  ('pizza ao quadrado'), ('wraps'), ('telepizza'), ('pizzaria speranza'),
  ('bom bocado'), ('paris 6'), ('le vin'), ('suplicy'), ('havanna'),
  ('confeitaria colombo'), ('cacau show'), ('kopenhagen'), ('mundo verde'),
  ('crepe mania'), ('porcao'), ('rafain'), ('ribs'), ('tordesilhas'),
  ('mocoto'), ('restaurante spot'), ('mani'), ('paris beirut'), ('sushi leblon'),
  ('aoyama'), ('bubba gump'), ('roasty')
) AS p(pattern);


-- ─────────────────────────────────────────────────────────────────────────────
-- TRANSPORTE
-- ─────────────────────────────────────────────────────────────────────────────

-- Uber / Taxi
-- Skipped: '99' alone (too short — matches invoice numbers, percentages, etc.)
--          Use '99app' which appears in descriptions as '99APP*BR'
INSERT INTO banking.category_rules (account_id, category_id, pattern, pattern_type, applicable_to, priority, is_system)
SELECT NULL, cat.id, p.pattern, 'CONTAINS', 'EXPENSE', 100, true
FROM (SELECT id FROM banking.transaction_categories WHERE name = 'Uber / Taxi' AND is_system = true LIMIT 1) AS cat
CROSS JOIN (VALUES
  ('uber'), ('99app'), ('indrive'), ('cabify')
) AS p(pattern);

-- Combustível
-- Skipped: 'posto' (too broad — matches 'posto de saude', 'posto policial', etc.)
INSERT INTO banking.category_rules (account_id, category_id, pattern, pattern_type, applicable_to, priority, is_system)
SELECT NULL, cat.id, p.pattern, 'CONTAINS', 'EXPENSE', 100, true
FROM (SELECT id FROM banking.transaction_categories WHERE name = 'Combustível' AND is_system = true LIMIT 1) AS cat
CROSS JOIN (VALUES
  ('ipiranga'), ('shell'), ('br distribuidora'), ('ale combustiveis'),
  ('vibra energia'), ('raizen'), ('texaco')
) AS p(pattern);

-- Pedágio
INSERT INTO banking.category_rules (account_id, category_id, pattern, pattern_type, applicable_to, priority, is_system)
SELECT NULL, cat.id, p.pattern, 'CONTAINS', 'EXPENSE', 100, true
FROM (SELECT id FROM banking.transaction_categories WHERE name = 'Pedágio' AND is_system = true LIMIT 1) AS cat
CROSS JOIN (VALUES
  ('sem parar'), ('conectcar'), ('veloe'), ('via facil'), ('ecovias')
) AS p(pattern);

-- Estacionamento
INSERT INTO banking.category_rules (account_id, category_id, pattern, pattern_type, applicable_to, priority, is_system)
SELECT NULL, cat.id, p.pattern, 'CONTAINS', 'EXPENSE', 100, true
FROM (SELECT id FROM banking.transaction_categories WHERE name = 'Estacionamento' AND is_system = true LIMIT 1) AS cat
CROSS JOIN (VALUES
  ('estapar'), ('indigo park'), ('multipark')
) AS p(pattern);

-- Transporte Público
-- Skipped: '1001', 'util', 'penha', 'otimo' — too short or too common as substrings
INSERT INTO banking.category_rules (account_id, category_id, pattern, pattern_type, applicable_to, priority, is_system)
SELECT NULL, cat.id, p.pattern, 'CONTAINS', 'EXPENSE', 100, true
FROM (SELECT id FROM banking.transaction_categories WHERE name = 'Transporte Público' AND is_system = true LIMIT 1) AS cat
CROSS JOIN (VALUES
  ('sptrans'), ('metro sp'), ('cptm'), ('supervia'), ('metro rio'),
  ('riocard'), ('vlt rio'), ('trensurb'), ('urbs curitiba'),
  ('buser'), ('flixbus'), ('itapemirim'), ('cometa'), ('real expresso'),
  ('gontijo'), ('pluma'), ('catarinense'), ('tembici'), ('grow bike')
) AS p(pattern);

-- Transporte (car rental)
INSERT INTO banking.category_rules (account_id, category_id, pattern, pattern_type, applicable_to, priority, is_system)
SELECT NULL, cat.id, p.pattern, 'CONTAINS', 'EXPENSE', 100, true
FROM (SELECT id FROM banking.transaction_categories WHERE name = 'Transporte' AND is_system = true LIMIT 1) AS cat
CROSS JOIN (VALUES
  ('localiza'), ('movida'), ('unidas rent'), ('hertz'), ('avis'), ('budget car')
) AS p(pattern);


-- ─────────────────────────────────────────────────────────────────────────────
-- SAÚDE
-- ─────────────────────────────────────────────────────────────────────────────

-- Farmácia
-- Using 'drogaria sao paulo' instead of 'dpsp' (too short; stores show full name)
INSERT INTO banking.category_rules (account_id, category_id, pattern, pattern_type, applicable_to, priority, is_system)
SELECT NULL, cat.id, p.pattern, 'CONTAINS', 'EXPENSE', 100, true
FROM (SELECT id FROM banking.transaction_categories WHERE name = 'Farmácia' AND is_system = true LIMIT 1) AS cat
CROSS JOIN (VALUES
  ('drogasil'), ('droga raia'), ('drogaria sao paulo'), ('ultrafarma'),
  ('pague menos'), ('panvel'), ('nissei'), ('extrafarma'), ('drogal'),
  ('araujo farma'), ('drogaria venancio'), ('farma conde'), ('onofre')
) AS p(pattern);

-- Plano de Saúde
INSERT INTO banking.category_rules (account_id, category_id, pattern, pattern_type, applicable_to, priority, is_system)
SELECT NULL, cat.id, p.pattern, 'CONTAINS', 'EXPENSE', 100, true
FROM (SELECT id FROM banking.transaction_categories WHERE name = 'Plano de Saúde' AND is_system = true LIMIT 1) AS cat
CROSS JOIN (VALUES
  ('unimed'), ('amil'), ('bradesco saude'), ('sulamerica saude'),
  ('hapvida'), ('notre dame intermedica'), ('prevent senior'), ('golden cross'),
  ('omint'), ('assim saude'), ('porto seguro saude'), ('cassi'), ('geap'),
  ('alice saude'), ('sami saude'), ('odontoprev'), ('uniodonto')
) AS p(pattern);

-- Academia
INSERT INTO banking.category_rules (account_id, category_id, pattern, pattern_type, applicable_to, priority, is_system)
SELECT NULL, cat.id, p.pattern, 'CONTAINS', 'EXPENSE', 100, true
FROM (SELECT id FROM banking.transaction_categories WHERE name = 'Academia' AND is_system = true LIMIT 1) AS cat
CROSS JOIN (VALUES
  ('smartfit'), ('bodytech'), ('bio ritmo'), ('bluefit'), ('golds gym'),
  ('gympass'), ('wellhub'), ('totalpass'), ('cia athletica')
) AS p(pattern);

-- Médico / Consulta
-- Normalized: 'rede d''or' → 'rede dor', 'dr. consulta' → 'dr consulta'
INSERT INTO banking.category_rules (account_id, category_id, pattern, pattern_type, applicable_to, priority, is_system)
SELECT NULL, cat.id, p.pattern, 'CONTAINS', 'EXPENSE', 100, true
FROM (SELECT id FROM banking.transaction_categories WHERE name = 'Médico / Consulta' AND is_system = true LIMIT 1) AS cat
CROSS JOIN (VALUES
  ('fleury'), ('dasa'), ('hermes pardini'), ('sabin'), ('lavoisier'),
  ('alta exames'), ('einstein'), ('sirio libanes'), ('rede dor'),
  ('bp beneficencia'), ('hcor'), ('moinhos de vento'), ('hospital oswaldo cruz'),
  ('hospital nove de julho'), ('dr consulta'), ('doctoralia'),
  ('conexa saude'), ('psicologia viva'), ('zenklub'), ('vittude')
) AS p(pattern);

-- Saúde (parent — óticas and other health)
INSERT INTO banking.category_rules (account_id, category_id, pattern, pattern_type, applicable_to, priority, is_system)
SELECT NULL, cat.id, p.pattern, 'CONTAINS', 'EXPENSE', 100, true
FROM (SELECT id FROM banking.transaction_categories WHERE name = 'Saúde' AND is_system = true LIMIT 1) AS cat
CROSS JOIN (VALUES
  ('oticas carol'), ('oticas diniz'), ('oticas penha'), ('grandvision'),
  ('formula e acao')
) AS p(pattern);


-- ─────────────────────────────────────────────────────────────────────────────
-- EDUCAÇÃO
-- ─────────────────────────────────────────────────────────────────────────────

-- Curso Online
-- Skipped: 'dio' (3 chars, too short)
INSERT INTO banking.category_rules (account_id, category_id, pattern, pattern_type, applicable_to, priority, is_system)
SELECT NULL, cat.id, p.pattern, 'CONTAINS', 'EXPENSE', 100, true
FROM (SELECT id FROM banking.transaction_categories WHERE name = 'Curso Online' AND is_system = true LIMIT 1) AS cat
CROSS JOIN (VALUES
  ('duolingo'), ('udemy'), ('coursera'), ('alura'), ('descomplica'),
  ('hotmart'), ('eduzz'), ('kiwify'), ('rocketseat'), ('pluralsight'),
  ('skillshare'), ('linkedin learning'), ('domestika'), ('masterclass'),
  ('babbel'), ('open english'), ('rosetta stone'), ('empiricus'),
  ('hashtag treinamentos')
) AS p(pattern);

-- Mensalidade
-- Skipped: 'coc', 'puc', 'ampli', 'eleva', 'cogna' (too short or too ambiguous)
-- Skipped: 'objetivo', 'positivo' (common Portuguese words), 'gap' (too short)
-- Skipped: 'santa cruz', 'bandeirantes' (neighborhood/team names in descriptions)
INSERT INTO banking.category_rules (account_id, category_id, pattern, pattern_type, applicable_to, priority, is_system)
SELECT NULL, cat.id, p.pattern, 'CONTAINS', 'EXPENSE', 100, true
FROM (SELECT id FROM banking.transaction_categories WHERE name = 'Mensalidade' AND is_system = true LIMIT 1) AS cat
CROSS JOIN (VALUES
  ('estacio'), ('anhanguera'), ('unopar'), ('unip'), ('fgv'),
  ('insper'), ('espm'), ('mackenzie'), ('faap'), ('ibmec'),
  ('wizard'), ('fisk'), ('ccaa'), ('cna'), ('cultura inglesa'),
  ('berlitz'), ('yazigi'), ('senai'), ('senac'), ('sebrae'),
  ('maple bear'), ('dante alighieri'), ('etapa'), ('marista'),
  ('salesiano'), ('bernoulli'), ('pitagoras'), ('poliedro'),
  ('ph cursinho'), ('cruzeiro do sul'), ('sao judas'), ('belas artes'),
  ('uniseb')
) AS p(pattern);

-- Material Escolar
INSERT INTO banking.category_rules (account_id, category_id, pattern, pattern_type, applicable_to, priority, is_system)
SELECT NULL, cat.id, p.pattern, 'CONTAINS', 'EXPENSE', 100, true
FROM (SELECT id FROM banking.transaction_categories WHERE name = 'Material Escolar' AND is_system = true LIMIT 1) AS cat
CROSS JOIN (VALUES
  ('saraiva'), ('livraria cultura'), ('kalunga')
) AS p(pattern);


-- ─────────────────────────────────────────────────────────────────────────────
-- SERVIÇOS DIGITAIS
-- ─────────────────────────────────────────────────────────────────────────────

-- Streaming Vídeo
-- Skipped: 'max' standalone (too short/common), 'sky' (too short)
-- 'disney' covers both 'disney+' and 'disney plus'; 'paramount' covers 'paramount+' and 'paramount plus'
INSERT INTO banking.category_rules (account_id, category_id, pattern, pattern_type, applicable_to, priority, is_system)
SELECT NULL, cat.id, p.pattern, 'CONTAINS', 'EXPENSE', 100, true
FROM (SELECT id FROM banking.transaction_categories WHERE name = 'Streaming Vídeo' AND is_system = true LIMIT 1) AS cat
CROSS JOIN (VALUES
  ('netflix'), ('disney'), ('amazon prime'), ('prime video'), ('hbo max'),
  ('globoplay'), ('paramount'), ('apple tv'), ('youtube premium'),
  ('crunchyroll'), ('telecine'), ('looke'), ('mubi'), ('sky brasil'),
  ('premiere'), ('dazn')
) AS p(pattern);

-- Streaming Música
INSERT INTO banking.category_rules (account_id, category_id, pattern, pattern_type, applicable_to, priority, is_system)
SELECT NULL, cat.id, p.pattern, 'CONTAINS', 'EXPENSE', 100, true
FROM (SELECT id FROM banking.transaction_categories WHERE name = 'Streaming Música' AND is_system = true LIMIT 1) AS cat
CROSS JOIN (VALUES
  ('spotify'), ('deezer'), ('apple music'), ('amazon music'),
  ('tidal'), ('audible'), ('storytel')
) AS p(pattern);

-- Software / Apps (generic software subscriptions)
INSERT INTO banking.category_rules (account_id, category_id, pattern, pattern_type, applicable_to, priority, is_system)
SELECT NULL, cat.id, p.pattern, 'CONTAINS', 'EXPENSE', 100, true
FROM (SELECT id FROM banking.transaction_categories WHERE name = 'Software / Apps' AND is_system = true LIMIT 1) AS cat
CROSS JOIN (VALUES
  ('microsoft'), ('adobe'), ('google one'), ('icloud'), ('dropbox'),
  ('notion'), ('slack'), ('zoom'), ('github'), ('chatgpt'), ('openai')
) AS p(pattern);


-- ─────────────────────────────────────────────────────────────────────────────
-- LAZER E ENTRETENIMENTO
-- ─────────────────────────────────────────────────────────────────────────────

-- Cinema / Teatro
INSERT INTO banking.category_rules (account_id, category_id, pattern, pattern_type, applicable_to, priority, is_system)
SELECT NULL, cat.id, p.pattern, 'CONTAINS', 'EXPENSE', 100, true
FROM (SELECT id FROM banking.transaction_categories WHERE name = 'Cinema / Teatro' AND is_system = true LIMIT 1) AS cat
CROSS JOIN (VALUES
  ('cinemark'), ('cinepolis'), ('uci cinemas'), ('cinesystem'),
  ('kinoplex'), ('ingresso.com')
) AS p(pattern);

-- Lazer e Entretenimento (events/ticketing)
INSERT INTO banking.category_rules (account_id, category_id, pattern, pattern_type, applicable_to, priority, is_system)
SELECT NULL, cat.id, p.pattern, 'CONTAINS', 'EXPENSE', 100, true
FROM (SELECT id FROM banking.transaction_categories WHERE name = 'Lazer e Entretenimento' AND is_system = true LIMIT 1) AS cat
CROSS JOIN (VALUES
  ('sympla'), ('eventbrite'), ('ticketmaster')
) AS p(pattern);


-- ─────────────────────────────────────────────────────────────────────────────
-- MORADIA / UTILIDADES
-- ─────────────────────────────────────────────────────────────────────────────

-- Luz (energy companies)
-- Skipped: 'light' (common English/Portuguese word), 'edp' (3 chars), 'rge' (3 chars)
INSERT INTO banking.category_rules (account_id, category_id, pattern, pattern_type, applicable_to, priority, is_system)
SELECT NULL, cat.id, p.pattern, 'CONTAINS', 'EXPENSE', 100, true
FROM (SELECT id FROM banking.transaction_categories WHERE name = 'Luz' AND is_system = true LIMIT 1) AS cat
CROSS JOIN (VALUES
  ('enel'), ('eletropaulo'), ('cemig'), ('copel'), ('cpfl'),
  ('energisa'), ('celesc'), ('neoenergia'), ('coelba'), ('elektro'),
  ('ceee'), ('equatorial energia'), ('celpe'), ('cosern'), ('coelce'), ('celg')
) AS p(pattern);

-- Água (water/sanitation companies)
-- Skipped: 'casan' (4 chars, too short), 'deso' (4 chars)
INSERT INTO banking.category_rules (account_id, category_id, pattern, pattern_type, applicable_to, priority, is_system)
SELECT NULL, cat.id, p.pattern, 'CONTAINS', 'EXPENSE', 100, true
FROM (SELECT id FROM banking.transaction_categories WHERE name = 'Água' AND is_system = true LIMIT 1) AS cat
CROSS JOIN (VALUES
  ('sabesp'), ('cedae'), ('sanepar'), ('embasa'), ('caesb'),
  ('copasa'), ('cagece'), ('corsan'), ('saneago'), ('aegea'),
  ('brk ambiental'), ('caern'), ('sanesul'), ('caema'), ('cosama'), ('agespisa')
) AS p(pattern);

-- Gás
-- Skipped: 'ceg' (3 chars), 'scgas' (5 chars but not distinctive), 'algas' (means algae)
INSERT INTO banking.category_rules (account_id, category_id, pattern, pattern_type, applicable_to, priority, is_system)
SELECT NULL, cat.id, p.pattern, 'CONTAINS', 'EXPENSE', 100, true
FROM (SELECT id FROM banking.transaction_categories WHERE name = 'Gás' AND is_system = true LIMIT 1) AS cat
CROSS JOIN (VALUES
  ('comgas'), ('gasmig'), ('naturgy'), ('bahiagas'),
  ('supergasbras'), ('ultragaz'), ('copagaz'), ('liquigas'), ('copergas')
) AS p(pattern);

-- Telecomunicações (parent — plan type unknown: could be mobile, internet or cable)
-- Skipped: 'tim' (3 chars), 'oi' (2 chars)
INSERT INTO banking.category_rules (account_id, category_id, pattern, pattern_type, applicable_to, priority, is_system)
SELECT NULL, cat.id, p.pattern, 'CONTAINS', 'EXPENSE', 100, true
FROM (SELECT id FROM banking.transaction_categories WHERE name = 'Telecomunicações' AND is_system = true LIMIT 1) AS cat
CROSS JOIN (VALUES
  ('claro'), ('vivo'), ('algar telecom'), ('brisanet'),
  ('unifique'), ('surf telecom')
) AS p(pattern);

-- Internet (known fixed-broadband-only providers)
INSERT INTO banking.category_rules (account_id, category_id, pattern, pattern_type, applicable_to, priority, is_system)
SELECT NULL, cat.id, p.pattern, 'CONTAINS', 'EXPENSE', 100, true
FROM (SELECT id FROM banking.transaction_categories WHERE name = 'Internet' AND is_system = true LIMIT 1) AS cat
CROSS JOIN (VALUES
  ('starlink'), ('desktop fibra'), ('mob telecom')
) AS p(pattern);


-- ─────────────────────────────────────────────────────────────────────────────
-- SEGUROS
-- ─────────────────────────────────────────────────────────────────────────────

-- Seguros (parent — subtype not inferrable from provider name alone)
-- Note: 'porto seguro saude' (Plano de Saúde) is seeded separately above,
--       so 'porto seguro' here correctly catches auto/property/life insurance.
INSERT INTO banking.category_rules (account_id, category_id, pattern, pattern_type, applicable_to, priority, is_system)
SELECT NULL, cat.id, p.pattern, 'CONTAINS', 'EXPENSE', 100, true
FROM (SELECT id FROM banking.transaction_categories WHERE name = 'Seguros' AND is_system = true LIMIT 1) AS cat
CROSS JOIN (VALUES
  ('porto seguro seguros'), ('bradesco seguros'), ('mapfre'), ('allianz'),
  ('liberty seguros'), ('hdi seguros'), ('tokio marine'), ('zurich'), ('sompo')
) AS p(pattern);


-- ─────────────────────────────────────────────────────────────────────────────
-- COMPRAS
-- ─────────────────────────────────────────────────────────────────────────────

-- Eletrônicos (specialist electronics retailers)
INSERT INTO banking.category_rules (account_id, category_id, pattern, pattern_type, applicable_to, priority, is_system)
SELECT NULL, cat.id, p.pattern, 'CONTAINS', 'EXPENSE', 100, true
FROM (SELECT id FROM banking.transaction_categories WHERE name = 'Eletrônicos' AND is_system = true LIMIT 1) AS cat
CROSS JOIN (VALUES
  ('kabum'), ('terabyte'), ('pichau'), ('fastshop')
) AS p(pattern);

-- Compras (general e-commerce)
-- Skipped: 'ponto' standalone (too short), 'wish' (common word), 'olx' (3 chars)
-- 'mercado pago' is a payment gateway but often appears as the merchant in card statements
INSERT INTO banking.category_rules (account_id, category_id, pattern, pattern_type, applicable_to, priority, is_system)
SELECT NULL, cat.id, p.pattern, 'CONTAINS', 'EXPENSE', 100, true
FROM (SELECT id FROM banking.transaction_categories WHERE name = 'Compras' AND is_system = true LIMIT 1) AS cat
CROSS JOIN (VALUES
  ('amazon'), ('mercado livre'), ('mercadolivre'), ('shopee'),
  ('americanas'), ('submarino'), ('shoptime'), ('magazine luiza'), ('magalu'),
  ('casas bahia'), ('ponto frio'), ('netshoes'), ('zattini'), ('dafiti'),
  ('shein'), ('temu'), ('aliexpress'), ('ebay'), ('etsy'), ('enjoei'),
  ('elo7'), ('beleza na web'), ('epoca cosmeticos'), ('iherb'), ('natue')
) AS p(pattern);


-- ─────────────────────────────────────────────────────────────────────────────
-- MODA
-- ─────────────────────────────────────────────────────────────────────────────

-- Roupas
-- Skipped: 'farm' (matches 'farmacia'), 'vans' (too short), 'gap' (too short),
--          'lupo' (too short), 'mixed' (common word), 'forum' (common word),
--          'ellus' (5 chars but very niche), 'salinas' (beach neighborhood name)
-- Normalized: 'h&m' → 'hm', 'levi''s' → 'levis', 'ray-ban' → 'ray ban'
INSERT INTO banking.category_rules (account_id, category_id, pattern, pattern_type, applicable_to, priority, is_system)
SELECT NULL, cat.id, p.pattern, 'CONTAINS', 'EXPENSE', 100, true
FROM (SELECT id FROM banking.transaction_categories WHERE name = 'Roupas' AND is_system = true LIMIT 1) AS cat
CROSS JOIN (VALUES
  ('renner'), ('cea'), ('riachuelo'), ('zara'), ('hm'),
  ('marisa'), ('hering'), ('animale'), ('le lis blanc'), ('colcci'),
  ('osklen'), ('richards'), ('reserva'), ('malwee'),
  ('nike'), ('adidas'), ('puma'), ('new balance'), ('under armour'),
  ('converse'), ('calvin klein'), ('tommy hilfiger'), ('lacoste'),
  ('ralph lauren'), ('levis'), ('arezzo'), ('schutz'), ('anacapri'),
  ('havaianas'), ('melissa'), ('piccadilly'), ('ramarim'), ('santa lolla'),
  ('via uno'), ('dumond'), ('cia maritima'), ('blue man'), ('chilli beans'),
  ('oakley'), ('ray ban')
) AS p(pattern);


-- ─────────────────────────────────────────────────────────────────────────────
-- RENDA (INCOME patterns — not from the merchant CSV, kept from V0_26)
-- ─────────────────────────────────────────────────────────────────────────────

INSERT INTO banking.category_rules (account_id, category_id, pattern, pattern_type, applicable_to, priority, is_system)
SELECT NULL, cat.id, p.pattern, 'CONTAINS', 'INCOME', 100, true
FROM (SELECT id FROM banking.transaction_categories WHERE name = 'Salário' AND is_system = true LIMIT 1) AS cat
CROSS JOIN (VALUES
  ('salario'), ('folha pagamento'), ('folha de pagamento'),
  ('pagamento salario'), ('holerite')
) AS p(pattern);

-- ─────────────────────────────────────────────────────────────────────────────
-- PIX
-- ─────────────────────────────────────────────────────────────────────────────
-- Adds a dedicated system category "PIX" (applicable_to = ANY) so PIX can be
-- suggested for both credit (INCOME) and debit (EXPENSE) transactions.
-- Then seeds system rules matching normalized descriptions containing "pix".

-- Create category PIX (applicable_to = ANY) if missing.
INSERT INTO banking.transaction_categories
    (parent_id, name, applicable_to, icon, color, is_system, sort_order)
SELECT
    NULL,
    'PIX',
    'ANY',
    'pix',
    '#1976D2',
    true,
    260
WHERE NOT EXISTS (
    SELECT 1
    FROM banking.transaction_categories
    WHERE name = 'PIX' AND applicable_to = 'ANY' AND is_system = true AND account_id IS NULL
);

-- PIX (suggest whenever description contains pix)
INSERT INTO banking.category_rules (account_id, category_id, pattern, pattern_type, applicable_to, priority, is_system)
SELECT NULL, cat.id, p.pattern, 'CONTAINS', a.applicable_to, 100, true
FROM (SELECT id FROM banking.transaction_categories WHERE name = 'PIX' AND applicable_to = 'ANY' AND is_system = true LIMIT 1) AS cat
CROSS JOIN (VALUES ('pix')) AS p(pattern)
CROSS JOIN (VALUES ('INCOME'), ('TRANSFER'), ('EXPENSE')) AS a(applicable_to);
