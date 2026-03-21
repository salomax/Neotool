SET search_path TO banking, public;

-- ─────────────────────────────────────────────────────────────────────────────
-- Merchants table
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE banking.merchants (
    id         UUID        NOT NULL DEFAULT gen_random_uuid(),
    name       VARCHAR(200) NOT NULL,
    domain     VARCHAR(200),
    sector     VARCHAR(100),
    is_system  BOOLEAN     NOT NULL DEFAULT false,
    account_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_merchants PRIMARY KEY (id)
);

-- System merchants: unique name within the global catalog
CREATE UNIQUE INDEX uq_merchants_system_name
    ON banking.merchants (name)
    WHERE account_id IS NULL AND is_system = true;

-- User merchants: unique name per account
CREATE UNIQUE INDEX uq_merchants_user_name
    ON banking.merchants (account_id, name)
    WHERE account_id IS NOT NULL;

-- Fast lookup by domain (logo cache join, hydration job)
CREATE INDEX idx_merchants_domain ON banking.merchants (domain) WHERE domain IS NOT NULL;

-- Fast lookup by account (suggestion query)
CREATE INDEX idx_merchants_account_id ON banking.merchants (account_id) WHERE account_id IS NOT NULL;

-- ─────────────────────────────────────────────────────────────────────────────
-- Merchant logo cache — domain-keyed, shared across all merchants
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE banking.merchant_logo_cache (
    domain     VARCHAR(200) NOT NULL,
    asset_id   VARCHAR(36)  NOT NULL,  -- UUID returned by the assets service
    fetched_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_merchant_logo_cache PRIMARY KEY (domain)
);

-- ─────────────────────────────────────────────────────────────────────────────
-- System merchant seed (derived from merchants-seed.csv)
-- Domains that contain a path segment (e.g. "ikea.com/br") are stored with
-- just the registrable domain so logo.dev can resolve them correctly.
-- ─────────────────────────────────────────────────────────────────────────────

INSERT INTO banking.merchants (name, domain, sector, is_system, account_id)
VALUES
  -- alimentacao
  ('carrefour',              'carrefour.com.br',          'alimentacao', true, NULL),
  ('atacadao',               'atacadao.com.br',           'alimentacao', true, NULL),
  ('assai',                  'assai.com.br',              'alimentacao', true, NULL),
  ('pao de acucar',          'grupopaodeacucar.com.br',   'alimentacao', true, NULL),
  ('extra',                  'grupopaodeacucar.com.br',   'alimentacao', true, NULL),
  ('sams club',              'samsclub.com.br',           'alimentacao', true, NULL),
  ('makro',                  'makro.com.br',              'alimentacao', true, NULL),
  ('spani',                  'spani.com.br',              'alimentacao', true, NULL),
  ('condor',                 'supercondor.com.br',        'alimentacao', true, NULL),
  ('muffato',                'supermuffato.com.br',       'alimentacao', true, NULL),
  ('angeloni',               'angeloni.com.br',           'alimentacao', true, NULL),
  ('giassi',                 'giassi.com.br',             'alimentacao', true, NULL),
  ('zaffari',                'zaffari.com.br',            'alimentacao', true, NULL),
  ('prezunic',               'prezunic.com.br',           'alimentacao', true, NULL),
  ('zona sul',               'zonasul.com.br',            'alimentacao', true, NULL),
  ('bahamas',                'superbahamas.com.br',       'alimentacao', true, NULL),
  ('bretas',                 'bretas.com.br',             'alimentacao', true, NULL),
  ('verdemar',               'verdemar.com.br',           'alimentacao', true, NULL),
  ('bh supermercados',       'bhsupermercados.com.br',    'alimentacao', true, NULL),
  ('hirota',                 'hirota.com.br',             'alimentacao', true, NULL),
  ('hortifruti',             'hortifruti.com.br',         'alimentacao', true, NULL),
  ('oba hortifruti',         'oba.com.br',                'alimentacao', true, NULL),
  ('natural da terra',       'naturaldaterra.com.br',     'alimentacao', true, NULL),
  ('saint marche',           'saintmarche.com.br',        'alimentacao', true, NULL),
  ('sonda',                  'sonda.com.br',              'alimentacao', true, NULL),
  ('comper',                 'comper.com.br',             'alimentacao', true, NULL),
  ('nacional supermercados', 'supernacional.com.br',      'alimentacao', true, NULL),
  -- delivery
  ('ifood',                  'ifood.com.br',              'delivery',    true, NULL),
  ('rappi',                  'rappi.com.br',              'delivery',    true, NULL),
  ('uber eats',              'ubereats.com',              'delivery',    true, NULL),
  -- restaurante
  ('mcdonalds',              'mcdonalds.com.br',          'restaurante', true, NULL),
  ('mc donalds',             'mcdonalds.com.br',          'restaurante', true, NULL),
  ('burger king',            'burgerking.com.br',         'restaurante', true, NULL),
  ('subway',                 'subway.com',                'restaurante', true, NULL),
  ('kfc',                    'kfc.com.br',                'restaurante', true, NULL),
  ('pizza hut',              'pizzahut.com.br',           'restaurante', true, NULL),
  ('dominos',                'dominos.com.br',            'restaurante', true, NULL),
  ('bobs',                   'bobs.com.br',               'restaurante', true, NULL),
  ('giraffas',               'giraffas.com.br',           'restaurante', true, NULL),
  ('habibs',                 'habibs.com.br',             'restaurante', true, NULL),
  ('spoleto',                'spoleto.com.br',            'restaurante', true, NULL),
  ('madero',                 'restaurantemadero.com.br',  'restaurante', true, NULL),
  ('china in box',           'chinainbox.com.br',         'restaurante', true, NULL),
  ('ragazzo',                'ragazzo.com.br',            'restaurante', true, NULL),
  ('jeronimo',               'jeronimo.com.br',           'restaurante', true, NULL),
  ('frango assado',          'frangoassado.com.br',       'restaurante', true, NULL),
  ('outback',                'outback.com.br',            'restaurante', true, NULL),
  ('starbucks',              'starbucks.com.br',          'restaurante', true, NULL),
  ('oakberry',               'oakberry.com',              'restaurante', true, NULL),
  ('taco bell',              'tacobell.com',              'restaurante', true, NULL),
  ('five guys',              'fiveguys.com',              'restaurante', true, NULL),
  ('popeyes',                'popeyes.com',               'restaurante', true, NULL),
  ('dunkin',                 'dunkindonuts.com.br',       'restaurante', true, NULL),
  ('koni',                   'koni.com.br',               'restaurante', true, NULL),
  ('baked potato',           'bakedpotato.com.br',        'restaurante', true, NULL),
  ('rei do mate',            'reidomate.com.br',          'restaurante', true, NULL),
  ('casa do pao de queijo',  'casadopaodequeijo.com.br',  'restaurante', true, NULL),
  ('fran''s cafe',           'franscafe.com.br',          'restaurante', true, NULL),
  ('blend burguer',          'blendburguer.com.br',       'restaurante', true, NULL),
  ('coco bambu',             'cocobambu.com.br',          'restaurante', true, NULL),
  ('vivenda do camarao',     'vivendadocamarao.com.br',   'restaurante', true, NULL),
  ('almanara',               'almanara.com.br',           'restaurante', true, NULL),
  ('fogo de chao',           'fogodechao.com',            'restaurante', true, NULL),
  ('pobre juan',             'pobrejuan.com.br',          'restaurante', true, NULL),
  ('gendai',                 'gendai.com.br',             'restaurante', true, NULL),
  ('camarada camarao',       'camaradacamarao.com.br',    'restaurante', true, NULL),
  ('patroni',                'patroni.com.br',            'restaurante', true, NULL),
  ('parme',                  'parme.com.br',              'restaurante', true, NULL),
  ('montana grill',          'montanagrill.com.br',       'restaurante', true, NULL),
  ('divino fogao',           'divinofogao.com.br',        'restaurante', true, NULL),
  ('chilis',                 'chilis.com.br',             'restaurante', true, NULL),
  ('applebees',              'applebees.com.br',          'restaurante', true, NULL),
  ('hard rock cafe',         'hardrock.com',              'restaurante', true, NULL),
  ('sushiloko',              'sushiloko.com.br',          'restaurante', true, NULL),
  ('temakeria',              'temakeria.com.br',          'restaurante', true, NULL),
  ('hokkai',                 'hokkai.com.br',             'restaurante', true, NULL),
  ('bibi sucos',             'bibisucos.com.br',          'restaurante', true, NULL),
  ('rascal',                 'rascal.com.br',             'restaurante', true, NULL),
  ('ofner',                  'ofner.com.br',              'restaurante', true, NULL),
  ('arabia',                 'arabia.com.br',             'restaurante', true, NULL),
  ('barbacoa',               'barbacoa.com.br',           'restaurante', true, NULL),
  ('rubaiyat',               'rubaiyat.com.br',           'restaurante', true, NULL),
  ('le pain quotidien',      'lepainquotidien.com',       'restaurante', true, NULL),
  ('mr cheney',              'mrcheney.com.br',           'restaurante', true, NULL),
  ('forneria san paolo',     'forneriasanpaolo.com.br',   'restaurante', true, NULL),
  ('yogoberry',              'yogoberry.com.br',          'restaurante', true, NULL),
  ('pitaya',                 'pitaya.com.br',             'restaurante', true, NULL),
  ('bella paulista',         'bellapaulista.com.br',      'restaurante', true, NULL),
  ('tenda do morango',       'tendadomorango.com.br',     'restaurante', true, NULL),
  ('cook''n roll',           'cooknroll.com.br',          'restaurante', true, NULL),
  ('d''accorsi',             'daccorsi.com.br',           'restaurante', true, NULL),
  ('bullguer',               'bullguer.com.br',           'restaurante', true, NULL),
  ('lanchonete da cidade',   'lanchoneteadacidade.com.br','restaurante', true, NULL),
  ('fifties',                'fifties.com.br',            'restaurante', true, NULL),
  ('braz pizzaria',          'pizzariabraz.com.br',       'restaurante', true, NULL),
  ('familia mancini',        'familiamancini.com.br',     'restaurante', true, NULL),
  ('viena',                  'viena.com.br',              'restaurante', true, NULL),
  ('amor aos pedacos',       'amoraospedacos.com.br',     'restaurante', true, NULL),
  ('freddo',                 'freddo.com.br',             'restaurante', true, NULL),
  ('bacio di latte',         'baciodilatte.com',          'restaurante', true, NULL),
  ('mil frutas',             'milfrutas.com.br',          'restaurante', true, NULL),
  ('haagen-dazs',            'haagendazs.com',            'restaurante', true, NULL),
  ('hooters',                'hooters.com.br',            'restaurante', true, NULL),
  ('tgi fridays',            'tgifridays.com.br',         'restaurante', true, NULL),
  ('pf changs',              'pfchangs.com.br',           'restaurante', true, NULL),
  ('nagayama',               'nagayama.com.br',           'restaurante', true, NULL),
  ('shoji',                  'shojirestaurante.com.br',   'restaurante', true, NULL),
  ('salad creations',        'saladcreations.com.br',     'restaurante', true, NULL),
  ('copa cafe',              'copacafe.com.br',           'restaurante', true, NULL),
  ('quitandinha',            'quitandinha.com.br',        'restaurante', true, NULL),
  ('cumbuca',                'restaurantecumbuca.com.br', 'restaurante', true, NULL),
  ('santa massa',            'santamassa.com.br',         'restaurante', true, NULL),
  ('nonno ruggero',          'nonnoruggero.com.br',       'restaurante', true, NULL),
  ('pizza ao quadrado',      'pizzaaoquadrado.com.br',    'restaurante', true, NULL),
  ('wraps',                  'wrapsbrasil.com.br',        'restaurante', true, NULL),
  ('telepizza',              'telepizza.com.br',          'restaurante', true, NULL),
  ('pizzaria speranza',      'pizzariasperanza.com.br',   'restaurante', true, NULL),
  ('bom bocado',             'bombocado.com.br',          'restaurante', true, NULL),
  ('paris 6',                'paris6.com.br',             'restaurante', true, NULL),
  ('le vin',                 'levin.com.br',              'restaurante', true, NULL),
  ('suplicy',                'suplicy.com.br',            'restaurante', true, NULL),
  ('havanna',                'havanna.com.br',            'restaurante', true, NULL),
  ('confeitaria colombo',    'confeitariacolombo.com.br', 'restaurante', true, NULL),
  ('cacau show',             'cacaushow.com.br',          'restaurante', true, NULL),
  ('kopenhagen',             'kopenhagen.com.br',         'restaurante', true, NULL),
  ('mundo verde',            'mundoverde.com.br',         'restaurante', true, NULL),
  ('crepe mania',            'crepemania.com.br',         'restaurante', true, NULL),
  ('porcao',                 'porcao.com.br',             'restaurante', true, NULL),
  ('rafain',                 'rafain.com.br',             'restaurante', true, NULL),
  ('ribs',                   'ribs.com.br',               'restaurante', true, NULL),
  ('tordesilhas',            'tordesilhas.com.br',        'restaurante', true, NULL),
  ('mocoto',                 'mocoto.com.br',             'restaurante', true, NULL),
  ('spot',                   'restaurantespot.com.br',    'restaurante', true, NULL),
  ('mani',                   'manirestaurante.com.br',    'restaurante', true, NULL),
  ('paris beirut',           'parisbeirut.com.br',        'restaurante', true, NULL),
  ('sushi leblon',           'sushileblon.com.br',        'restaurante', true, NULL),
  ('aoyama',                 'aoyama.com.br',             'restaurante', true, NULL),
  ('bubba gump',             'bubbagump.com',             'restaurante', true, NULL),
  ('roasty',                 'roasty.com.br',             'restaurante', true, NULL),
  -- transporte
  ('uber',                   'uber.com',                  'transporte',  true, NULL),
  ('99',                     '99app.com',                 'transporte',  true, NULL),
  ('indrive',                'indrive.com',               'transporte',  true, NULL),
  ('cabify',                 'cabify.com',                'transporte',  true, NULL),
  ('ipiranga',               'ipiranga.com.br',           'transporte',  true, NULL),
  ('shell',                  'shell.com.br',              'transporte',  true, NULL),
  ('br distribuidora',       'br.com.br',                 'transporte',  true, NULL),
  ('ale combustiveis',       'alecombustiveis.com.br',    'transporte',  true, NULL),
  ('vibra energia',          'vibraenergia.com.br',       'transporte',  true, NULL),
  ('sem parar',              'semparar.com.br',           'transporte',  true, NULL),
  ('conectcar',              'conectcar.com.br',          'transporte',  true, NULL),
  ('veloe',                  'veloe.com.br',              'transporte',  true, NULL),
  ('estapar',                'estapar.com.br',            'transporte',  true, NULL),
  ('indigo',                 'indigoneo.com.br',          'transporte',  true, NULL),
  ('localiza',               'localiza.com',              'transporte',  true, NULL),
  ('movida',                 'movida.com.br',             'transporte',  true, NULL),
  ('unidas',                 'unidas.com.br',             'transporte',  true, NULL),
  ('hertz',                  'hertz.com.br',              'transporte',  true, NULL),
  ('avis',                   'avis.com.br',               'transporte',  true, NULL),
  ('budget',                 'budget.com.br',             'transporte',  true, NULL),
  ('sptrans',                'sptrans.com.br',            'transporte',  true, NULL),
  ('metro sp',               'metro.sp.gov.br',           'transporte',  true, NULL),
  ('cptm',                   'cptm.sp.gov.br',            'transporte',  true, NULL),
  ('supervia',               'supervia.com.br',           'transporte',  true, NULL),
  ('metro rio',              'metrorio.com.br',           'transporte',  true, NULL),
  ('buser',                  'buser.com.br',              'transporte',  true, NULL),
  ('flixbus',                'flixbus.com.br',            'transporte',  true, NULL),
  ('itapemirim',             'itapemirim.com.br',         'transporte',  true, NULL),
  ('cometa',                 'viacao-cometa.com.br',      'transporte',  true, NULL),
  ('raizen',                 'raizen.com.br',             'transporte',  true, NULL),
  ('texaco',                 'texaco.com.br',             'transporte',  true, NULL),
  ('via facil',              'viafacil.com.br',           'transporte',  true, NULL),
  ('ecovias',                'ecovias.com.br',            'transporte',  true, NULL),
  ('multipark',              'multipark.com.br',          'transporte',  true, NULL),
  ('riocard',                'riocard.com.br',            'transporte',  true, NULL),
  ('vlt rio',                'vltrio.com.br',             'transporte',  true, NULL),
  ('trensurb',               'trensurb.gov.br',           'transporte',  true, NULL),
  ('urbs',                   'urbs.curitiba.pr.gov.br',   'transporte',  true, NULL),
  ('otimo',                  'otimo.com.br',              'transporte',  true, NULL),
  ('tembici',                'tembici.com.br',            'transporte',  true, NULL),
  ('grow',                   'grow.com.br',               'transporte',  true, NULL),
  ('real expresso',          'realexpresso.com.br',       'transporte',  true, NULL),
  ('util',                   'util.com.br',               'transporte',  true, NULL),
  ('1001',                   'autoviacao1001.com.br',     'transporte',  true, NULL),
  ('gontijo',                'gontijo-turismo.com.br',    'transporte',  true, NULL),
  ('penha',                  'viacaopenha.com.br',        'transporte',  true, NULL),
  ('pluma',                  'pluma.com.br',              'transporte',  true, NULL),
  ('catarinense',            'catarinense.net',           'transporte',  true, NULL),
  -- streaming
  ('netflix',                'netflix.com',               'streaming',   true, NULL),
  ('disney+',                'disneyplus.com',            'streaming',   true, NULL),
  ('disney plus',            'disneyplus.com',            'streaming',   true, NULL),
  ('amazon prime',           'primevideo.com',            'streaming',   true, NULL),
  ('prime video',            'primevideo.com',            'streaming',   true, NULL),
  ('hbo max',                'max.com',                   'streaming',   true, NULL),
  ('max',                    'max.com',                   'streaming',   true, NULL),
  ('globoplay',              'globoplay.globo.com',        'streaming',   true, NULL),
  ('paramount+',             'paramountplus.com',         'streaming',   true, NULL),
  ('paramount plus',         'paramountplus.com',         'streaming',   true, NULL),
  ('apple tv',               'apple.com',                 'streaming',   true, NULL),
  ('youtube premium',        'youtube.com',               'streaming',   true, NULL),
  ('crunchyroll',            'crunchyroll.com',           'streaming',   true, NULL),
  ('telecine',               'telecine.com.br',           'streaming',   true, NULL),
  ('looke',                  'looke.com.br',              'streaming',   true, NULL),
  ('mubi',                   'mubi.com',                  'streaming',   true, NULL),
  ('spotify',                'spotify.com',               'streaming',   true, NULL),
  ('deezer',                 'deezer.com',                'streaming',   true, NULL),
  ('apple music',            'apple.com',                 'streaming',   true, NULL),
  ('amazon music',           'music.amazon.com',          'streaming',   true, NULL),
  ('tidal',                  'tidal.com',                 'streaming',   true, NULL),
  ('audible',                'audible.com',               'streaming',   true, NULL),
  ('storytel',               'storytel.com',              'streaming',   true, NULL),
  ('sky',                    'sky.com.br',                'streaming',   true, NULL),
  ('premiere',               'premierfc.globo.com',       'streaming',   true, NULL),
  ('dazn',                   'dazn.com',                  'streaming',   true, NULL),
  ('cinemark',               'cinemark.com.br',           'streaming',   true, NULL),
  ('cinepolis',              'cinepolis.com.br',          'streaming',   true, NULL),
  ('uci cinemas',            'ucicinemas.com.br',         'streaming',   true, NULL),
  ('cinesystem',             'cinesystem.com.br',         'streaming',   true, NULL),
  ('kinoplex',               'kinoplex.com.br',           'streaming',   true, NULL),
  ('ingresso.com',           'ingresso.com',              'streaming',   true, NULL),
  ('sympla',                 'sympla.com.br',             'streaming',   true, NULL),
  ('eventbrite',             'eventbrite.com.br',         'streaming',   true, NULL),
  ('ticketmaster',           'ticketmaster.com.br',       'streaming',   true, NULL),
  -- saude
  ('drogasil',               'drogasil.com.br',           'saude',       true, NULL),
  ('droga raia',             'drogaraia.com.br',          'saude',       true, NULL),
  ('drogaria sao paulo',     'drogariasaopaulo.com.br',   'saude',       true, NULL),
  ('ultrafarma',             'ultrafarma.com.br',         'saude',       true, NULL),
  ('pague menos',            'paguemenos.com.br',         'saude',       true, NULL),
  ('panvel',                 'panvel.com.br',             'saude',       true, NULL),
  ('nissei',                 'nissei.com.br',             'saude',       true, NULL),
  ('extrafarma',             'extrafarma.com.br',         'saude',       true, NULL),
  ('drogal',                 'drogal.com.br',             'saude',       true, NULL),
  ('araujo',                 'araujo.com.br',             'saude',       true, NULL),
  ('dpsp',                   'dpsp.com.br',               'saude',       true, NULL),
  ('unimed',                 'unimed.com.br',             'saude',       true, NULL),
  ('amil',                   'amil.com.br',               'saude',       true, NULL),
  ('bradesco saude',         'bradescosaude.com.br',      'saude',       true, NULL),
  ('sulamerica saude',       'sulamerica.com.br',         'saude',       true, NULL),
  ('hapvida',                'hapvida.com.br',            'saude',       true, NULL),
  ('notre dame intermedica', 'gndi.com.br',               'saude',       true, NULL),
  ('prevent senior',         'preventsenior.com.br',      'saude',       true, NULL),
  ('golden cross',           'goldencross.com.br',        'saude',       true, NULL),
  ('fleury',                 'fleury.com.br',             'saude',       true, NULL),
  ('dasa',                   'dasa.com.br',               'saude',       true, NULL),
  ('hermes pardini',         'hermespardini.com.br',      'saude',       true, NULL),
  ('sabin',                  'sabin.com.br',              'saude',       true, NULL),
  ('lavoisier',              'lavoisier.com.br',          'saude',       true, NULL),
  ('alta exames',            'altaexames.com.br',         'saude',       true, NULL),
  ('einstein',               'einstein.br',               'saude',       true, NULL),
  ('sirio libanes',          'hospitalsiriolibanes.org.br','saude',       true, NULL),
  ('rede d''or',             'rededor.com.br',            'saude',       true, NULL),
  ('bp beneficencia portuguesa','bp.org.br',              'saude',       true, NULL),
  ('odontoprev',             'odontoprev.com.br',         'saude',       true, NULL),
  ('uniodonto',              'uniodonto.com.br',          'saude',       true, NULL),
  ('dr. consulta',           'drconsulta.com',            'saude',       true, NULL),
  ('doctoralia',             'doctoralia.com.br',         'saude',       true, NULL),
  ('conexa saude',           'conexasaude.com.br',        'saude',       true, NULL),
  ('smartfit',               'smartfit.com.br',           'saude',       true, NULL),
  ('bodytech',               'bodytech.com.br',           'saude',       true, NULL),
  ('bio ritmo',              'bioritmoacademia.com.br',   'saude',       true, NULL),
  ('bluefit',                'bluefit.com.br',            'saude',       true, NULL),
  ('golds gym',              'goldsgym.com.br',           'saude',       true, NULL),
  ('gympass',                'gympass.com',               'saude',       true, NULL),
  ('wellhub',                'wellhub.com',               'saude',       true, NULL),
  ('drogaria venancio',      'drogariasvenancio.com.br',  'saude',       true, NULL),
  ('farma conde',            'farmaconde.com.br',         'saude',       true, NULL),
  ('onofre',                 'onofre.com.br',             'saude',       true, NULL),
  ('omint',                  'omint.com.br',              'saude',       true, NULL),
  ('assim saude',            'assimdigital.com.br',       'saude',       true, NULL),
  ('porto seguro saude',     'portoseguro.com.br',        'saude',       true, NULL),
  ('cassi',                  'cassi.com.br',              'saude',       true, NULL),
  ('geap',                   'geap.com.br',               'saude',       true, NULL),
  ('alice saude',            'alice.com.br',              'saude',       true, NULL),
  ('sami saude',             'samisaude.com.br',          'saude',       true, NULL),
  ('hcor',                   'hcor.com.br',               'saude',       true, NULL),
  ('moinhos de vento',       'hmv.org.br',                'saude',       true, NULL),
  ('oswaldo cruz',           'haoc.com.br',               'saude',       true, NULL),
  ('hospital nove de julho', 'hospitalnovejulho.com.br',  'saude',       true, NULL),
  ('psicologia viva',        'psicologiaviva.com.br',     'saude',       true, NULL),
  ('zenklub',                'zenklub.com',               'saude',       true, NULL),
  ('vittude',                'vittude.com',               'saude',       true, NULL),
  ('totalpass',              'totalpass.com.br',          'saude',       true, NULL),
  ('cia athletica',          'ciaathletica.com.br',       'saude',       true, NULL),
  ('oticas carol',           'oticascarol.com.br',        'saude',       true, NULL),
  ('oticas diniz',           'oticasdiniz.com.br',        'saude',       true, NULL),
  ('oticas penha',           'oticaspenha.com.br',        'saude',       true, NULL),
  ('grandvision',            'grandvision.com.br',        'saude',       true, NULL),
  ('formula e acao',         'formulaeacao.com.br',       'saude',       true, NULL),
  -- educacao
  ('duolingo',               'duolingo.com',              'educacao',    true, NULL),
  ('udemy',                  'udemy.com',                 'educacao',    true, NULL),
  ('coursera',               'coursera.org',              'educacao',    true, NULL),
  ('alura',                  'alura.com.br',              'educacao',    true, NULL),
  ('descomplica',            'descomplica.com.br',        'educacao',    true, NULL),
  ('hotmart',                'hotmart.com',               'educacao',    true, NULL),
  ('eduzz',                  'eduzz.com',                 'educacao',    true, NULL),
  ('kiwify',                 'kiwify.com.br',             'educacao',    true, NULL),
  ('rocketseat',             'rocketseat.com.br',         'educacao',    true, NULL),
  ('dio',                    'dio.me',                    'educacao',    true, NULL),
  ('pluralsight',            'pluralsight.com',           'educacao',    true, NULL),
  ('skillshare',             'skillshare.com',            'educacao',    true, NULL),
  ('linkedin learning',      'linkedin.com',              'educacao',    true, NULL),
  ('estacio',                'estacio.br',                'educacao',    true, NULL),
  ('anhanguera',             'anhanguera.com',            'educacao',    true, NULL),
  ('unopar',                 'unopar.com.br',             'educacao',    true, NULL),
  ('unip',                   'unip.br',                   'educacao',    true, NULL),
  ('fgv',                    'fgv.br',                    'educacao',    true, NULL),
  ('insper',                 'insper.edu.br',             'educacao',    true, NULL),
  ('espm',                   'espm.br',                   'educacao',    true, NULL),
  ('mackenzie',              'mackenzie.br',              'educacao',    true, NULL),
  ('faap',                   'faap.br',                   'educacao',    true, NULL),
  ('ibmec',                  'ibmec.br',                  'educacao',    true, NULL),
  ('wizard',                 'wizard.com.br',             'educacao',    true, NULL),
  ('fisk',                   'fisk.com.br',               'educacao',    true, NULL),
  ('ccaa',                   'ccaa.com.br',               'educacao',    true, NULL),
  ('cna',                    'cna.com.br',                'educacao',    true, NULL),
  ('cultura inglesa',        'culturainglesa.com.br',     'educacao',    true, NULL),
  ('berlitz',                'berlitz.com.br',            'educacao',    true, NULL),
  ('yazigi',                 'yazigi.com.br',             'educacao',    true, NULL),
  ('senai',                  'senai.br',                  'educacao',    true, NULL),
  ('senac',                  'senac.br',                  'educacao',    true, NULL),
  ('sebrae',                 'sebrae.com.br',             'educacao',    true, NULL),
  ('saraiva',                'saraiva.com.br',            'educacao',    true, NULL),
  ('livraria cultura',       'livrariacultura.com.br',    'educacao',    true, NULL),
  ('kalunga',                'kalunga.com.br',            'educacao',    true, NULL),
  ('maple bear',             'maplebear.com.br',          'educacao',    true, NULL),
  ('coc',                    'coc.com.br',                'educacao',    true, NULL),
  ('anglo',                  'colegioeanglo.com.br',      'educacao',    true, NULL),
  ('objetivo',               'objetivo.br',               'educacao',    true, NULL),
  ('dante alighieri',        'dante.com.br',              'educacao',    true, NULL),
  ('santa cruz',             'colegiosantacruz.com.br',   'educacao',    true, NULL),
  ('bandeirantes',           'bandeirantes.com.br',       'educacao',    true, NULL),
  ('etapa',                  'etapa.com.br',              'educacao',    true, NULL),
  ('marista',                'marista.edu.br',            'educacao',    true, NULL),
  ('salesiano',              'salesiano.edu.br',          'educacao',    true, NULL),
  ('bernoulli',              'bernoulli.com.br',          'educacao',    true, NULL),
  ('positivo',               'colegiopositivo.com.br',    'educacao',    true, NULL),
  ('pitagoras',              'pitagoras.com.br',          'educacao',    true, NULL),
  ('poliedro',               'sistemapoliedro.com.br',    'educacao',    true, NULL),
  ('eleva',                  'elevaeducacao.com.br',      'educacao',    true, NULL),
  ('ph cursinho',            'ph.com.br',                 'educacao',    true, NULL),
  ('cogna',                  'cogna.com.br',              'educacao',    true, NULL),
  ('cruzeiro do sul',        'cruzeirodosul.edu.br',      'educacao',    true, NULL),
  ('sao judas',              'saojudas.br',               'educacao',    true, NULL),
  ('belas artes',            'belasartes.br',             'educacao',    true, NULL),
  ('puc',                    'pucsp.br',                  'educacao',    true, NULL),
  ('uniseb',                 'uniseb.com.br',             'educacao',    true, NULL),
  ('ampli',                  'ampli.com.br',              'educacao',    true, NULL),
  ('domestika',              'domestika.org',             'educacao',    true, NULL),
  ('masterclass',            'masterclass.com',           'educacao',    true, NULL),
  ('babbel',                 'babbel.com',                'educacao',    true, NULL),
  ('open english',           'openenglish.com',           'educacao',    true, NULL),
  ('rosetta stone',          'rosettastone.com',          'educacao',    true, NULL),
  ('empiricus',              'empiricus.com.br',          'educacao',    true, NULL),
  ('hashtag treinamentos',   'hashtagtreinamentos.com.br','educacao',    true, NULL),
  -- utilities
  ('enel',                   'enel.com.br',               'utilities',   true, NULL),
  ('eletropaulo',            'enel.com.br',               'utilities',   true, NULL),
  ('cemig',                  'cemig.com.br',              'utilities',   true, NULL),
  ('copel',                  'copel.com',                 'utilities',   true, NULL),
  ('cpfl',                   'cpfl.com.br',               'utilities',   true, NULL),
  ('light',                  'light.com.br',              'utilities',   true, NULL),
  ('energisa',               'energisa.com.br',           'utilities',   true, NULL),
  ('celesc',                 'celesc.com.br',             'utilities',   true, NULL),
  ('neoenergia',             'neoenergia.com',            'utilities',   true, NULL),
  ('coelba',                 'coelba.com.br',             'utilities',   true, NULL),
  ('elektro',                'elektro.com.br',            'utilities',   true, NULL),
  ('edp',                    'edp.com.br',                'utilities',   true, NULL),
  ('ceee',                   'ceee.com.br',               'utilities',   true, NULL),
  ('equatorial',             'equatorialenergia.com.br',  'utilities',   true, NULL),
  ('sabesp',                 'sabesp.com.br',             'utilities',   true, NULL),
  ('cedae',                  'cedae.com.br',              'utilities',   true, NULL),
  ('sanepar',                'sanepar.com.br',            'utilities',   true, NULL),
  ('embasa',                 'embasa.com.br',             'utilities',   true, NULL),
  ('caesb',                  'caesb.df.gov.br',           'utilities',   true, NULL),
  ('copasa',                 'copasa.com.br',             'utilities',   true, NULL),
  ('cagece',                 'cagece.com.br',             'utilities',   true, NULL),
  ('corsan',                 'corsan.com.br',             'utilities',   true, NULL),
  ('casan',                  'casan.com.br',              'utilities',   true, NULL),
  ('saneago',                'saneago.com.br',            'utilities',   true, NULL),
  ('aegea',                  'aegea.com.br',              'utilities',   true, NULL),
  ('brk ambiental',          'brkambiental.com.br',       'utilities',   true, NULL),
  ('comgas',                 'comgas.com.br',             'utilities',   true, NULL),
  ('ceg',                    'ceg.com.br',                'utilities',   true, NULL),
  ('gasmig',                 'gasmig.com.br',             'utilities',   true, NULL),
  ('naturgy',                'naturgy.com.br',            'utilities',   true, NULL),
  ('bahiagas',               'bahiagas.com.br',           'utilities',   true, NULL),
  ('claro',                  'claro.com.br',              'utilities',   true, NULL),
  ('vivo',                   'vivo.com.br',               'utilities',   true, NULL),
  ('tim',                    'tim.com.br',                'utilities',   true, NULL),
  ('oi',                     'oi.com.br',                 'utilities',   true, NULL),
  ('algar telecom',          'algartelecom.com.br',       'utilities',   true, NULL),
  ('brisanet',               'brisanet.com.br',           'utilities',   true, NULL),
  ('celpe',                  'celpe.com.br',              'utilities',   true, NULL),
  ('cosern',                 'cosern.com.br',             'utilities',   true, NULL),
  ('coelce',                 'coelce.com.br',             'utilities',   true, NULL),
  ('celg',                   'celg.com.br',               'utilities',   true, NULL),
  ('rge',                    'rge.com.br',                'utilities',   true, NULL),
  ('caern',                  'caern.com.br',              'utilities',   true, NULL),
  ('deso',                   'deso.com.br',               'utilities',   true, NULL),
  ('sanesul',                'sanesul.com.br',            'utilities',   true, NULL),
  ('caema',                  'caema.com.br',              'utilities',   true, NULL),
  ('cosama',                 'cosama.com.br',             'utilities',   true, NULL),
  ('agespisa',               'agespisa.com.br',           'utilities',   true, NULL),
  ('scgas',                  'scgas.com.br',              'utilities',   true, NULL),
  ('copergas',               'copergas.com.br',           'utilities',   true, NULL),
  ('algas',                  'algas.com.br',              'utilities',   true, NULL),
  ('supergasbras',           'supergasbras.com.br',       'utilities',   true, NULL),
  ('ultragaz',               'ultragaz.com.br',           'utilities',   true, NULL),
  ('copagaz',                'copagaz.com.br',            'utilities',   true, NULL),
  ('liquigas',               'liquigas.com.br',           'utilities',   true, NULL),
  ('starlink',               'starlink.com',              'utilities',   true, NULL),
  ('unifique',               'unifique.com.br',           'utilities',   true, NULL),
  ('desktop',                'desktop.com.br',            'utilities',   true, NULL),
  ('surf telecom',           'surftelecom.com.br',        'utilities',   true, NULL),
  ('porto seguro seguros',   'portoseguro.com.br',        'utilities',   true, NULL),
  ('bradesco seguros',       'bradescoseguros.com.br',    'utilities',   true, NULL),
  ('mapfre',                 'mapfre.com.br',             'utilities',   true, NULL),
  ('allianz',                'allianz.com.br',            'utilities',   true, NULL),
  ('liberty seguros',        'libertyseguros.com.br',     'utilities',   true, NULL),
  ('hdi seguros',            'hdiseguros.com.br',         'utilities',   true, NULL),
  ('tokio marine',           'tokiomarine.com.br',        'utilities',   true, NULL),
  ('zurich',                 'zurich.com.br',             'utilities',   true, NULL),
  ('sompo',                  'sompo.com.br',              'utilities',   true, NULL),
  -- ecommerce
  ('amazon',                 'amazon.com.br',             'ecommerce',   true, NULL),
  ('mercado livre',          'mercadolivre.com.br',       'ecommerce',   true, NULL),
  ('mercadolivre',           'mercadolivre.com.br',       'ecommerce',   true, NULL),
  ('mercado pago',           'mercadopago.com.br',        'ecommerce',   true, NULL),
  ('mercadopago',            'mercadopago.com.br',        'ecommerce',   true, NULL),
  ('shopee',                 'shopee.com.br',             'ecommerce',   true, NULL),
  ('americanas',             'americanas.com.br',         'ecommerce',   true, NULL),
  ('submarino',              'submarino.com.br',          'ecommerce',   true, NULL),
  ('shoptime',               'shoptime.com.br',           'ecommerce',   true, NULL),
  ('magazine luiza',         'magazineluiza.com.br',      'ecommerce',   true, NULL),
  ('magalu',                 'magazineluiza.com.br',      'ecommerce',   true, NULL),
  ('casas bahia',            'casasbahia.com.br',         'ecommerce',   true, NULL),
  ('ponto',                  'pontofrio.com.br',          'ecommerce',   true, NULL),
  ('ponto frio',             'pontofrio.com.br',          'ecommerce',   true, NULL),
  ('netshoes',               'netshoes.com.br',           'ecommerce',   true, NULL),
  ('zattini',                'zattini.com.br',            'ecommerce',   true, NULL),
  ('dafiti',                 'dafiti.com.br',             'ecommerce',   true, NULL),
  ('shein',                  'shein.com',                 'ecommerce',   true, NULL),
  ('temu',                   'temu.com',                  'ecommerce',   true, NULL),
  ('aliexpress',             'aliexpress.com',            'ecommerce',   true, NULL),
  ('wish',                   'wish.com',                  'ecommerce',   true, NULL),
  ('ebay',                   'ebay.com',                  'ecommerce',   true, NULL),
  ('etsy',                   'etsy.com',                  'ecommerce',   true, NULL),
  ('enjoei',                 'enjoei.com.br',             'ecommerce',   true, NULL),
  ('olx',                    'olx.com.br',                'ecommerce',   true, NULL),
  ('elo7',                   'elo7.com.br',               'ecommerce',   true, NULL),
  ('fastshop',               'fastshop.com.br',           'ecommerce',   true, NULL),
  ('kabum',                  'kabum.com.br',              'ecommerce',   true, NULL),
  ('terabyte',               'terabyteshop.com.br',       'ecommerce',   true, NULL),
  ('pichau',                 'pichau.com.br',             'ecommerce',   true, NULL),
  ('beleza na web',          'belezanaweb.com.br',        'ecommerce',   true, NULL),
  ('epoca cosmeticos',       'epocacosmeticos.com.br',    'ecommerce',   true, NULL),
  ('iherb',                  'iherb.com',                 'ecommerce',   true, NULL),
  ('natue',                  'natue.com.br',              'ecommerce',   true, NULL),
  -- moda
  ('renner',                 'lojasrenner.com.br',        'moda',        true, NULL),
  ('cea',                    'cea.com.br',                'moda',        true, NULL),
  ('riachuelo',              'riachuelo.com.br',          'moda',        true, NULL),
  ('zara',                   'zara.com',                  'moda',        true, NULL),
  ('hm',                     'hm.com',                    'moda',        true, NULL),
  ('marisa',                 'marisa.com.br',             'moda',        true, NULL),
  ('hering',                 'hering.com.br',             'moda',        true, NULL),
  ('farm',                   'farmrio.com',               'moda',        true, NULL),
  ('animale',                'animale.com.br',            'moda',        true, NULL),
  ('le lis blanc',           'lelisblanc.com.br',         'moda',        true, NULL),
  ('mixed',                  'mixed.com.br',              'moda',        true, NULL),
  ('colcci',                 'colcci.com.br',             'moda',        true, NULL),
  ('osklen',                 'osklen.com',                'moda',        true, NULL),
  ('ellus',                  'ellus.com.br',              'moda',        true, NULL),
  ('forum',                  'forum.com.br',              'moda',        true, NULL),
  ('richards',               'richards.com.br',           'moda',        true, NULL),
  ('reserva',                'reserva.com.br',            'moda',        true, NULL),
  ('malwee',                 'malwee.com.br',             'moda',        true, NULL),
  ('lupo',                   'lupo.com.br',               'moda',        true, NULL),
  ('nike',                   'nike.com.br',               'moda',        true, NULL),
  ('adidas',                 'adidas.com.br',             'moda',        true, NULL),
  ('puma',                   'puma.com',                  'moda',        true, NULL),
  ('new balance',            'newbalance.com.br',         'moda',        true, NULL),
  ('under armour',           'underarmour.com',           'moda',        true, NULL),
  ('vans',                   'vans.com',                  'moda',        true, NULL),
  ('converse',               'converse.com',              'moda',        true, NULL),
  ('calvin klein',           'calvinklein.com.br',        'moda',        true, NULL),
  ('tommy hilfiger',         'tommy.com',                 'moda',        true, NULL),
  ('lacoste',                'lacoste.com',               'moda',        true, NULL),
  ('ralph lauren',           'ralphlauren.com',           'moda',        true, NULL),
  ('levi''s',                'levi.com',                  'moda',        true, NULL),
  ('guess',                  'guess.com',                 'moda',        true, NULL),
  ('gap',                    'gap.com',                   'moda',        true, NULL),
  ('arezzo',                 'arezzo.com.br',             'moda',        true, NULL),
  ('schutz',                 'schutz.com.br',             'moda',        true, NULL),
  ('anacapri',               'anacapri.com.br',           'moda',        true, NULL),
  ('havaianas',              'havaianas.com',             'moda',        true, NULL),
  ('melissa',                'melissa.com.br',            'moda',        true, NULL),
  ('piccadilly',             'piccadilly.com.br',         'moda',        true, NULL),
  ('ramarim',                'ramarim.com.br',            'moda',        true, NULL),
  ('santa lolla',            'santalolla.com.br',         'moda',        true, NULL),
  ('via uno',                'viauno.com.br',             'moda',        true, NULL),
  ('dumond',                 'dumond.com.br',             'moda',        true, NULL),
  ('cia maritima',           'ciamaritima.com.br',        'moda',        true, NULL),
  ('blue man',               'blueman.com.br',            'moda',        true, NULL),
  ('salinas',                'salinasswimwear.com.br',    'moda',        true, NULL),
  ('chilli beans',           'chillibeans.com.br',        'moda',        true, NULL),
  ('oakley',                 'oakley.com',                'moda',        true, NULL),
  ('ray-ban',                'ray-ban.com',               'moda',        true, NULL),
  -- financas
  ('nubank',                 'nubank.com.br',             'financas',    true, NULL),
  ('itau',                   'itau.com.br',               'financas',    true, NULL),
  ('bradesco',               'bradesco.com.br',           'financas',    true, NULL),
  ('santander',              'santander.com.br',          'financas',    true, NULL),
  ('banco do brasil',        'bb.com.br',                 'financas',    true, NULL),
  ('caixa',                  'caixa.gov.br',              'financas',    true, NULL),
  ('btg pactual',            'btgpactual.com',            'financas',    true, NULL),
  ('xp investimentos',       'xp.com.br',                 'financas',    true, NULL),
  ('inter',                  'bancointer.com.br',         'financas',    true, NULL),
  ('c6 bank',                'c6bank.com.br',             'financas',    true, NULL),
  ('picpay',                 'picpay.com',                'financas',    true, NULL),
  ('pagseguro',              'pagseguro.com',             'financas',    true, NULL),
  ('paypal',                 'paypal.com',                'financas',    true, NULL),
  ('stone',                  'stone.com.br',              'financas',    true, NULL),
  ('cielo',                  'cielo.com.br',              'financas',    true, NULL),
  ('rede',                   'userede.com.br',            'financas',    true, NULL),
  ('getnet',                 'getnet.com.br',             'financas',    true, NULL),
  ('sicredi',                'sicredi.com.br',            'financas',    true, NULL),
  ('sicoob',                 'sicoob.com.br',             'financas',    true, NULL),
  ('cresol',                 'cresol.com.br',             'financas',    true, NULL),
  ('unicred',                'unicred.com.br',            'financas',    true, NULL),
  ('neon',                   'neon.com.br',               'financas',    true, NULL),
  ('next',                   'next.me',                   'financas',    true, NULL),
  ('agibank',                'agibank.com.br',            'financas',    true, NULL),
  ('pan',                    'bancopan.com.br',           'financas',    true, NULL),
  ('bmg',                    'bancobmg.com.br',           'financas',    true, NULL),
  ('daycoval',               'daycoval.com.br',           'financas',    true, NULL),
  ('safra',                  'safra.com.br',              'financas',    true, NULL),
  ('bv',                     'bancobv.com.br',            'financas',    true, NULL),
  ('original',               'bancoriginal.com.br',       'financas',    true, NULL),
  ('modal',                  'bancomodal.com.br',         'financas',    true, NULL),
  ('banrisul',               'banrisul.com.br',           'financas',    true, NULL),
  ('brb',                    'brb.com.br',                'financas',    true, NULL),
  ('banese',                 'banese.com.br',             'financas',    true, NULL),
  ('mercantil',              'mercantil.com.br',          'financas',    true, NULL),
  ('creditas',               'creditas.com',              'financas',    true, NULL),
  ('serasa',                 'serasa.com.br',             'financas',    true, NULL),
  ('wise',                   'wise.com',                  'financas',    true, NULL),
  ('remessa online',         'remessaonline.com.br',      'financas',    true, NULL),
  ('nomad',                  'nomadglobal.com',           'financas',    true, NULL),
  ('avenue',                 'avenue.us',                 'financas',    true, NULL),
  ('rico',                   'rico.com.vc',               'financas',    true, NULL),
  ('clear',                  'clear.com.br',              'financas',    true, NULL),
  ('nuinvest',               'nuinvest.com.br',           'financas',    true, NULL),
  ('warren',                 'warren.com.br',             'financas',    true, NULL),
  ('toro investimentos',     'toroinvestimentos.com.br',  'financas',    true, NULL),
  ('genial',                 'genialinvestimentos.com.br','financas',    true, NULL),
  ('orama',                  'orama.com.br',              'financas',    true, NULL),
  ('guide',                  'guideinvestimentos.com.br', 'financas',    true, NULL),
  ('agora',                  'agorainvestimentos.com.br', 'financas',    true, NULL),
  ('american express',       'americanexpress.com',       'financas',    true, NULL),
  ('western union',          'westernunion.com',          'financas',    true, NULL),
  -- viagens
  ('latam',                  'latam.com',                 'viagens',     true, NULL),
  ('gol',                    'voegol.com.br',             'viagens',     true, NULL),
  ('azul',                   'voeazul.com.br',            'viagens',     true, NULL),
  ('tap',                    'flytap.com',                'viagens',     true, NULL),
  ('american airlines',      'aa.com',                    'viagens',     true, NULL),
  ('united',                 'united.com',                'viagens',     true, NULL),
  ('delta',                  'delta.com',                 'viagens',     true, NULL),
  ('air france',             'airfrance.com',             'viagens',     true, NULL),
  ('klm',                    'klm.com',                   'viagens',     true, NULL),
  ('iberia',                 'iberia.com',                'viagens',     true, NULL),
  ('emirates',               'emirates.com',              'viagens',     true, NULL),
  ('turkish airlines',       'turkishairlines.com',       'viagens',     true, NULL),
  ('copa airlines',          'copaair.com',               'viagens',     true, NULL),
  ('airbnb',                 'airbnb.com',                'viagens',     true, NULL),
  ('booking',                'booking.com',               'viagens',     true, NULL),
  ('expedia',                'expedia.com.br',            'viagens',     true, NULL),
  ('decolar',                'decolar.com',               'viagens',     true, NULL),
  ('despegar',               'despegar.com',              'viagens',     true, NULL),
  ('trivago',                'trivago.com.br',            'viagens',     true, NULL),
  ('hotels.com',             'hotels.com',                'viagens',     true, NULL),
  ('cvc',                    'cvc.com.br',                'viagens',     true, NULL),
  ('viajanet',               'viajanet.com.br',           'viagens',     true, NULL),
  ('maxmilhas',              'maxmilhas.com.br',          'viagens',     true, NULL),
  ('smiles',                 'smiles.com.br',             'viagens',     true, NULL),
  ('latam pass',             'latampass.com',             'viagens',     true, NULL),
  ('tudoazul',               'tudoazul.com',              'viagens',     true, NULL),
  ('accor',                  'accor.com',                 'viagens',     true, NULL),
  ('ibis',                   'ibis.com',                  'viagens',     true, NULL),
  ('novotel',                'novotel.com',               'viagens',     true, NULL),
  ('hilton',                 'hilton.com',                'viagens',     true, NULL),
  ('marriott',               'marriott.com',              'viagens',     true, NULL),
  ('hyatt',                  'hyatt.com',                 'viagens',     true, NULL),
  ('best western',           'bestwestern.com',           'viagens',     true, NULL),
  ('intercontinental',       'ihg.com',                   'viagens',     true, NULL),
  ('holiday inn',            'ihg.com',                   'viagens',     true, NULL),
  ('radisson',               'radisson.com',              'viagens',     true, NULL),
  ('golden tulip',           'goldentulip.com',           'viagens',     true, NULL),
  ('windsor',                'windsorhoteis.com.br',      'viagens',     true, NULL),
  ('blue tree',              'bluetree.com.br',           'viagens',     true, NULL),
  ('club med',               'clubmed.com.br',            'viagens',     true, NULL),
  -- games
  ('steam',                  'store.steampowered.com',    'games',       true, NULL),
  ('playstation',            'playstation.com',           'games',       true, NULL),
  ('xbox',                   'xbox.com',                  'games',       true, NULL),
  ('nintendo',               'nintendo.com.br',           'games',       true, NULL),
  ('epic games',             'epicgames.com',             'games',       true, NULL),
  ('riot games',             'riotgames.com',             'games',       true, NULL),
  ('blizzard',               'blizzard.com',              'games',       true, NULL),
  ('ea games',               'ea.com',                    'games',       true, NULL),
  ('ubisoft',                'ubisoft.com',               'games',       true, NULL),
  ('rockstar games',         'rockstargames.com',         'games',       true, NULL),
  ('twitch',                 'twitch.tv',                 'games',       true, NULL),
  ('discord',                'discord.com',               'games',       true, NULL),
  -- tecnologia
  ('google play',            'play.google.com',           'tecnologia',  true, NULL),
  ('apple',                  'apple.com',                 'tecnologia',  true, NULL),
  ('icloud',                 'apple.com',                 'tecnologia',  true, NULL),
  ('microsoft',              'microsoft.com',             'tecnologia',  true, NULL),
  ('google one',             'one.google.com',            'tecnologia',  true, NULL),
  ('adobe',                  'adobe.com',                 'tecnologia',  true, NULL),
  ('canva',                  'canva.com',                 'tecnologia',  true, NULL),
  ('figma',                  'figma.com',                 'tecnologia',  true, NULL),
  ('dropbox',                'dropbox.com',               'tecnologia',  true, NULL),
  ('github',                 'github.com',                'tecnologia',  true, NULL),
  ('openai',                 'openai.com',                'tecnologia',  true, NULL),
  ('chatgpt',                'openai.com',                'tecnologia',  true, NULL),
  ('samsung',                'samsung.com.br',            'tecnologia',  true, NULL),
  ('dell',                   'dell.com',                  'tecnologia',  true, NULL),
  ('lenovo',                 'lenovo.com',                'tecnologia',  true, NULL),
  ('hp',                     'hp.com',                    'tecnologia',  true, NULL),
  ('positivo tecnologia',    'positivotecnologia.com.br', 'tecnologia',  true, NULL),
  ('motorola',               'motorola.com.br',           'tecnologia',  true, NULL),
  ('xiaomi',                 'mi.com',                    'tecnologia',  true, NULL),
  ('logitech',               'logitech.com',              'tecnologia',  true, NULL),
  ('razer',                  'razer.com',                 'tecnologia',  true, NULL),
  ('nordvpn',                'nordvpn.com',               'tecnologia',  true, NULL),
  ('expressvpn',             'expressvpn.com',            'tecnologia',  true, NULL),
  ('zoom',                   'zoom.us',                   'tecnologia',  true, NULL),
  ('notion',                 'notion.so',                 'tecnologia',  true, NULL),
  ('aws',                    'aws.amazon.com',            'tecnologia',  true, NULL),
  ('digitalocean',           'digitalocean.com',          'tecnologia',  true, NULL),
  ('cloudflare',             'cloudflare.com',            'tecnologia',  true, NULL),
  -- casa
  ('leroy merlin',           'leroymerlin.com.br',        'casa',        true, NULL),
  ('tokstok',                'tokstok.com.br',            'casa',        true, NULL),
  ('ikea',                   'ikea.com',                  'casa',        true, NULL),
  ('madeiramadeira',         'madeiramadeira.com.br',     'casa',        true, NULL),
  ('camicado',               'camicado.com.br',           'casa',        true, NULL),
  ('westwing',               'westwing.com.br',           'casa',        true, NULL),
  ('mobly',                  'mobly.com.br',              'casa',        true, NULL),
  ('telhanorte',             'telhanorte.com.br',         'casa',        true, NULL),
  ('quero-quero',            'queroquero.com.br',         'casa',        true, NULL),
  ('havan',                  'havan.com.br',              'casa',        true, NULL),
  ('colombo',                'colombo.com.br',            'casa',        true, NULL),
  ('tramontina',             'tramontina.com.br',         'casa',        true, NULL),
  ('ri happy',               'rihappy.com.br',            'casa',        true, NULL),
  ('pbkids',                 'pbkids.com.br',             'casa',        true, NULL),
  ('lego',                   'lego.com',                  'casa',        true, NULL),
  ('brastemp',               'brastemp.com.br',           'casa',        true, NULL),
  ('consul',                 'consul.com.br',             'casa',        true, NULL),
  ('electrolux',             'electrolux.com.br',         'casa',        true, NULL),
  ('bosch',                  'bosch.com.br',              'casa',        true, NULL),
  ('philips',                'philips.com.br',            'casa',        true, NULL),
  ('multilaser',             'multilaser.com.br',         'casa',        true, NULL),
  ('mondial',                'mondial.com.br',            'casa',        true, NULL),
  ('britania',               'britania.com.br',           'casa',        true, NULL),
  ('arno',                   'arno.com.br',               'casa',        true, NULL),
  ('oster',                  'osterbrasil.com.br',        'casa',        true, NULL),
  ('tok stok',               'tokstok.com.br',            'casa',        true, NULL),
  ('casa e video',           'casaevideo.com.br',         'casa',        true, NULL),
  ('etna',                   'etna.com.br',               'casa',        true, NULL),
  ('cassol',                 'cassol.com.br',             'casa',        true, NULL),
  ('c&c',                    'cec.com.br',                'casa',        true, NULL),
  -- pet
  ('petlove',                'petlove.com.br',            'pet',         true, NULL),
  ('petz',                   'petz.com.br',               'pet',         true, NULL),
  ('cobasi',                 'cobasi.com.br',             'pet',         true, NULL),
  ('zee dog',                'zee.dog',                   'pet',         true, NULL),
  ('pet center',             'petcenter.com.br',          'pet',         true, NULL),
  ('doghero',                'doghero.com.br',            'pet',         true, NULL),
  ('pet anjo',               'petanjo.com',               'pet',         true, NULL),
  ('dr. pet',                'drpet.com.br',              'pet',         true, NULL),
  ('agrocat',                'agrocat.com.br',            'pet',         true, NULL),
  ('purina',                 'purina.com.br',             'pet',         true, NULL),
  ('royal canin',            'royalcanin.com',            'pet',         true, NULL),
  ('hills',                  'hillspet.com',              'pet',         true, NULL),
  ('golden',                 'goldenalimentos.com.br',    'pet',         true, NULL),
  ('premier pet',            'premierpet.com.br',         'pet',         true, NULL),
  ('guabi',                  'guabi.com.br',              'pet',         true, NULL),
  ('biofresh',               'biofresh.com.br',           'pet',         true, NULL),
  ('racao total',            'racaototal.com.br',         'pet',         true, NULL),
  ('granplus',               'granplus.com.br',           'pet',         true, NULL),
  ('tudo de bicho',          'tudodebicho.com.br',        'pet',         true, NULL),
  ('agropet',                'agropet.com.br',            'pet',         true, NULL),
  ('mundo animal',           'mundoanimal.com.br',        'pet',         true, NULL),
  ('vetshop',                'vetshop.com.br',            'pet',         true, NULL),
  ('aminopet',               'aminopet.com.br',           'pet',         true, NULL),
  ('petland',                'petland.com.br',            'pet',         true, NULL),
  -- beleza
  ('o boticario',            'boticario.com.br',          'beleza',      true, NULL),
  ('natura',                 'natura.com.br',             'beleza',      true, NULL),
  ('avon',                   'avon.com.br',               'beleza',      true, NULL),
  ('sephora',                'sephora.com.br',            'beleza',      true, NULL),
  ('loreal',                 'loreal.com.br',             'beleza',      true, NULL),
  ('eudora',                 'eudora.com.br',             'beleza',      true, NULL),
  ('vult',                   'vult.com.br',               'beleza',      true, NULL),
  ('quem disse berenice',    'quemdisseberenice.com.br',  'beleza',      true, NULL),
  ('ruby rose',              'rubyrose.com.br',           'beleza',      true, NULL),
  ('mac cosmetics',          'maccosmetics.com.br',       'beleza',      true, NULL),
  ('maybelline',             'maybelline.com.br',         'beleza',      true, NULL),
  ('nivea',                  'nivea.com.br',              'beleza',      true, NULL),
  ('neutrogena',             'neutrogena.com.br',         'beleza',      true, NULL),
  ('la roche-posay',         'laroche-posay.com.br',      'beleza',      true, NULL),
  ('cerave',                 'cerave.com.br',             'beleza',      true, NULL),
  ('vichy',                  'vichy.com.br',              'beleza',      true, NULL),
  ('wella',                  'wella.com',                 'beleza',      true, NULL),
  ('schwarzkopf',            'schwarzkopf.com.br',        'beleza',      true, NULL),
  ('garnier',                'garnier.com.br',            'beleza',      true, NULL),
  ('salon line',             'salonline.com.br',          'beleza',      true, NULL),
  ('ikesaki',                'ikesaki.com.br',            'beleza',      true, NULL),
  ('studio 35',              'studio35.com.br',           'beleza',      true, NULL),
  ('jequiti',                'jequiti.com.br',            'beleza',      true, NULL),
  ('mary kay',               'marykay.com.br',            'beleza',      true, NULL),
  ('hinode',                 'hinode.com.br',             'beleza',      true, NULL),
  ('amway',                  'amway.com.br',              'beleza',      true, NULL),
  ('gillette',               'gillette.com.br',           'beleza',      true, NULL),
  ('dove',                   'dove.com',                  'beleza',      true, NULL),
  ('nars',                   'narscosmetics.com',         'beleza',      true, NULL),
  ('urban decay',            'urbandecay.com',            'beleza',      true, NULL),
  ('anastasia beverly hills','anastasia.net',             'beleza',      true, NULL),
  ('the ordinary',           'theordinary.com',           'beleza',      true, NULL),
  ('beauty color',           'beautycolor.com.br',        'beleza',      true, NULL)
ON CONFLICT DO NOTHING;

-- Final auto-classification rule table after category_rules -> transaction_rules broadening.
-- Seed data lives in ../seeds/40_transaction_rules.sqlfrag.

SET search_path TO banking, public;

CREATE TABLE banking.transaction_rules (
    id            UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    account_id    UUID,
    category_id   UUID REFERENCES banking.transaction_categories(id),
    merchant_id   UUID REFERENCES banking.merchants(id) ON DELETE SET NULL,
    pattern       TEXT        NOT NULL,
    pattern_type  TEXT        NOT NULL
        CHECK (pattern_type IN ('CONTAINS', 'STARTS_WITH', 'EXACT', 'REGEX')),
    applicable_to TEXT        NOT NULL
        CHECK (applicable_to IN ('INCOME', 'EXPENSE', 'TRANSFER', 'ANY')),
    priority      INT         NOT NULL DEFAULT 100,
    is_system     BOOLEAN     NOT NULL DEFAULT false,
    is_implicit   BOOLEAN     NOT NULL DEFAULT false,
    match_count   INT         NOT NULL DEFAULT 0,
    deleted_at    TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_transaction_rules_has_target
        CHECK (category_id IS NOT NULL OR merchant_id IS NOT NULL)
);

CREATE INDEX idx_category_rules_account_id
    ON banking.transaction_rules(account_id)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_category_rules_system
    ON banking.transaction_rules(is_system)
    WHERE is_system = true AND deleted_at IS NULL;

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
DELETE FROM banking.transaction_rules
WHERE is_system = true AND account_id IS NULL AND is_implicit = false;


-- ─────────────────────────────────────────────────────────────────────────────
-- ALIMENTAÇÃO
-- ─────────────────────────────────────────────────────────────────────────────

-- Supermercado
-- Skipped: 'extra' (matches 'extrafarma' → false positive on pharmacy transactions)
INSERT INTO banking.transaction_rules (account_id, category_id, pattern, pattern_type, applicable_to, priority, is_system)
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
INSERT INTO banking.transaction_rules (account_id, category_id, pattern, pattern_type, applicable_to, priority, is_system)
SELECT NULL, cat.id, p.pattern, 'CONTAINS', 'EXPENSE', 100, true
FROM (SELECT id FROM banking.transaction_categories WHERE name = 'Delivery' AND is_system = true LIMIT 1) AS cat
CROSS JOIN (VALUES
  ('ifood'), ('rappi'), ('uber eats')
) AS p(pattern);

-- Restaurante
-- Normalized: 'ráscal' → 'rascal', 'fran''s cafe' → 'frans cafe',
--             'cook''n roll' → 'cookn roll', 'd''accorsi' → 'daccorsi',
--             'mc donalds' kept as separate pattern from 'mcdonalds'
INSERT INTO banking.transaction_rules (account_id, category_id, pattern, pattern_type, applicable_to, priority, is_system)
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
INSERT INTO banking.transaction_rules (account_id, category_id, pattern, pattern_type, applicable_to, priority, is_system)
SELECT NULL, cat.id, p.pattern, 'CONTAINS', 'EXPENSE', 100, true
FROM (SELECT id FROM banking.transaction_categories WHERE name = 'Uber / Taxi' AND is_system = true LIMIT 1) AS cat
CROSS JOIN (VALUES
  ('uber'), ('99app'), ('indrive'), ('cabify')
) AS p(pattern);

-- Combustível
-- Skipped: 'posto' (too broad — matches 'posto de saude', 'posto policial', etc.)
INSERT INTO banking.transaction_rules (account_id, category_id, pattern, pattern_type, applicable_to, priority, is_system)
SELECT NULL, cat.id, p.pattern, 'CONTAINS', 'EXPENSE', 100, true
FROM (SELECT id FROM banking.transaction_categories WHERE name = 'Combustível' AND is_system = true LIMIT 1) AS cat
CROSS JOIN (VALUES
  ('ipiranga'), ('shell'), ('br distribuidora'), ('ale combustiveis'),
  ('vibra energia'), ('raizen'), ('texaco')
) AS p(pattern);

-- Pedágio
INSERT INTO banking.transaction_rules (account_id, category_id, pattern, pattern_type, applicable_to, priority, is_system)
SELECT NULL, cat.id, p.pattern, 'CONTAINS', 'EXPENSE', 100, true
FROM (SELECT id FROM banking.transaction_categories WHERE name = 'Pedágio' AND is_system = true LIMIT 1) AS cat
CROSS JOIN (VALUES
  ('sem parar'), ('conectcar'), ('veloe'), ('via facil'), ('ecovias')
) AS p(pattern);

-- Estacionamento
INSERT INTO banking.transaction_rules (account_id, category_id, pattern, pattern_type, applicable_to, priority, is_system)
SELECT NULL, cat.id, p.pattern, 'CONTAINS', 'EXPENSE', 100, true
FROM (SELECT id FROM banking.transaction_categories WHERE name = 'Estacionamento' AND is_system = true LIMIT 1) AS cat
CROSS JOIN (VALUES
  ('estapar'), ('indigo park'), ('multipark')
) AS p(pattern);

-- Transporte Público
-- Skipped: '1001', 'util', 'penha', 'otimo' — too short or too common as substrings
INSERT INTO banking.transaction_rules (account_id, category_id, pattern, pattern_type, applicable_to, priority, is_system)
SELECT NULL, cat.id, p.pattern, 'CONTAINS', 'EXPENSE', 100, true
FROM (SELECT id FROM banking.transaction_categories WHERE name = 'Transporte Público' AND is_system = true LIMIT 1) AS cat
CROSS JOIN (VALUES
  ('sptrans'), ('metro sp'), ('cptm'), ('supervia'), ('metro rio'),
  ('riocard'), ('vlt rio'), ('trensurb'), ('urbs curitiba'),
  ('buser'), ('flixbus'), ('itapemirim'), ('cometa'), ('real expresso'),
  ('gontijo'), ('pluma'), ('catarinense'), ('tembici'), ('grow bike')
) AS p(pattern);

-- Transporte (car rental)
INSERT INTO banking.transaction_rules (account_id, category_id, pattern, pattern_type, applicable_to, priority, is_system)
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
INSERT INTO banking.transaction_rules (account_id, category_id, pattern, pattern_type, applicable_to, priority, is_system)
SELECT NULL, cat.id, p.pattern, 'CONTAINS', 'EXPENSE', 100, true
FROM (SELECT id FROM banking.transaction_categories WHERE name = 'Farmácia' AND is_system = true LIMIT 1) AS cat
CROSS JOIN (VALUES
  ('drogasil'), ('droga raia'), ('drogaria sao paulo'), ('ultrafarma'),
  ('pague menos'), ('panvel'), ('nissei'), ('extrafarma'), ('drogal'),
  ('araujo farma'), ('drogaria venancio'), ('farma conde'), ('onofre')
) AS p(pattern);

-- Plano de Saúde
INSERT INTO banking.transaction_rules (account_id, category_id, pattern, pattern_type, applicable_to, priority, is_system)
SELECT NULL, cat.id, p.pattern, 'CONTAINS', 'EXPENSE', 100, true
FROM (SELECT id FROM banking.transaction_categories WHERE name = 'Plano de Saúde' AND is_system = true LIMIT 1) AS cat
CROSS JOIN (VALUES
  ('unimed'), ('amil'), ('bradesco saude'), ('sulamerica saude'),
  ('hapvida'), ('notre dame intermedica'), ('prevent senior'), ('golden cross'),
  ('omint'), ('assim saude'), ('porto seguro saude'), ('cassi'), ('geap'),
  ('alice saude'), ('sami saude'), ('odontoprev'), ('uniodonto')
) AS p(pattern);

-- Academia
INSERT INTO banking.transaction_rules (account_id, category_id, pattern, pattern_type, applicable_to, priority, is_system)
SELECT NULL, cat.id, p.pattern, 'CONTAINS', 'EXPENSE', 100, true
FROM (SELECT id FROM banking.transaction_categories WHERE name = 'Academia' AND is_system = true LIMIT 1) AS cat
CROSS JOIN (VALUES
  ('smartfit'), ('bodytech'), ('bio ritmo'), ('bluefit'), ('golds gym'),
  ('gympass'), ('wellhub'), ('totalpass'), ('cia athletica')
) AS p(pattern);

-- Médico / Consulta
-- Normalized: 'rede d''or' → 'rede dor', 'dr. consulta' → 'dr consulta'
INSERT INTO banking.transaction_rules (account_id, category_id, pattern, pattern_type, applicable_to, priority, is_system)
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
INSERT INTO banking.transaction_rules (account_id, category_id, pattern, pattern_type, applicable_to, priority, is_system)
SELECT NULL, cat.id, p.pattern, 'CONTAINS', 'EXPENSE', 100, true
FROM (SELECT id FROM banking.transaction_categories WHERE name = 'Saúde' AND is_system = true LIMIT 1) AS cat
CROSS JOIN (VALUES
  ('oticas carol'), ('oticas diniz'), ('oticas penha'), ('grandvision'),
  ('formula e acao')
) AS p(pattern);


-- ─────────────────────────────────────────────────────────────────────────────

-- Link system rules to their matching system merchants when the normalized
-- pattern equals the normalized merchant name.
UPDATE banking.transaction_rules tr
SET merchant_id = m.id
FROM banking.merchants m
WHERE tr.is_system = true
  AND tr.account_id IS NULL
  AND tr.pattern = m.name
  AND m.is_system = true
  AND tr.merchant_id IS NULL;
-- EDUCAÇÃO
-- ─────────────────────────────────────────────────────────────────────────────

-- Curso Online
-- Skipped: 'dio' (3 chars, too short)
INSERT INTO banking.transaction_rules (account_id, category_id, pattern, pattern_type, applicable_to, priority, is_system)
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
INSERT INTO banking.transaction_rules (account_id, category_id, pattern, pattern_type, applicable_to, priority, is_system)
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
INSERT INTO banking.transaction_rules (account_id, category_id, pattern, pattern_type, applicable_to, priority, is_system)
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
INSERT INTO banking.transaction_rules (account_id, category_id, pattern, pattern_type, applicable_to, priority, is_system)
SELECT NULL, cat.id, p.pattern, 'CONTAINS', 'EXPENSE', 100, true
FROM (SELECT id FROM banking.transaction_categories WHERE name = 'Streaming Vídeo' AND is_system = true LIMIT 1) AS cat
CROSS JOIN (VALUES
  ('netflix'), ('disney'), ('amazon prime'), ('prime video'), ('hbo max'),
  ('globoplay'), ('paramount'), ('apple tv'), ('youtube premium'),
  ('crunchyroll'), ('telecine'), ('looke'), ('mubi'), ('sky brasil'),
  ('premiere'), ('dazn')
) AS p(pattern);

-- Streaming Música
INSERT INTO banking.transaction_rules (account_id, category_id, pattern, pattern_type, applicable_to, priority, is_system)
SELECT NULL, cat.id, p.pattern, 'CONTAINS', 'EXPENSE', 100, true
FROM (SELECT id FROM banking.transaction_categories WHERE name = 'Streaming Música' AND is_system = true LIMIT 1) AS cat
CROSS JOIN (VALUES
  ('spotify'), ('deezer'), ('apple music'), ('amazon music'),
  ('tidal'), ('audible'), ('storytel')
) AS p(pattern);

-- Software / Apps (generic software subscriptions)
INSERT INTO banking.transaction_rules (account_id, category_id, pattern, pattern_type, applicable_to, priority, is_system)
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
INSERT INTO banking.transaction_rules (account_id, category_id, pattern, pattern_type, applicable_to, priority, is_system)
SELECT NULL, cat.id, p.pattern, 'CONTAINS', 'EXPENSE', 100, true
FROM (SELECT id FROM banking.transaction_categories WHERE name = 'Cinema / Teatro' AND is_system = true LIMIT 1) AS cat
CROSS JOIN (VALUES
  ('cinemark'), ('cinepolis'), ('uci cinemas'), ('cinesystem'),
  ('kinoplex'), ('ingresso.com')
) AS p(pattern);

-- Lazer e Entretenimento (events/ticketing)
INSERT INTO banking.transaction_rules (account_id, category_id, pattern, pattern_type, applicable_to, priority, is_system)
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
INSERT INTO banking.transaction_rules (account_id, category_id, pattern, pattern_type, applicable_to, priority, is_system)
SELECT NULL, cat.id, p.pattern, 'CONTAINS', 'EXPENSE', 100, true
FROM (SELECT id FROM banking.transaction_categories WHERE name = 'Luz' AND is_system = true LIMIT 1) AS cat
CROSS JOIN (VALUES
  ('enel'), ('eletropaulo'), ('cemig'), ('copel'), ('cpfl'),
  ('energisa'), ('celesc'), ('neoenergia'), ('coelba'), ('elektro'),
  ('ceee'), ('equatorial energia'), ('celpe'), ('cosern'), ('coelce'), ('celg')
) AS p(pattern);

-- Água (water/sanitation companies)
-- Skipped: 'casan' (4 chars, too short), 'deso' (4 chars)
INSERT INTO banking.transaction_rules (account_id, category_id, pattern, pattern_type, applicable_to, priority, is_system)
SELECT NULL, cat.id, p.pattern, 'CONTAINS', 'EXPENSE', 100, true
FROM (SELECT id FROM banking.transaction_categories WHERE name = 'Água' AND is_system = true LIMIT 1) AS cat
CROSS JOIN (VALUES
  ('sabesp'), ('cedae'), ('sanepar'), ('embasa'), ('caesb'),
  ('copasa'), ('cagece'), ('corsan'), ('saneago'), ('aegea'),
  ('brk ambiental'), ('caern'), ('sanesul'), ('caema'), ('cosama'), ('agespisa')
) AS p(pattern);

-- Gás
-- Skipped: 'ceg' (3 chars), 'scgas' (5 chars but not distinctive), 'algas' (means algae)
INSERT INTO banking.transaction_rules (account_id, category_id, pattern, pattern_type, applicable_to, priority, is_system)
SELECT NULL, cat.id, p.pattern, 'CONTAINS', 'EXPENSE', 100, true
FROM (SELECT id FROM banking.transaction_categories WHERE name = 'Gás' AND is_system = true LIMIT 1) AS cat
CROSS JOIN (VALUES
  ('comgas'), ('gasmig'), ('naturgy'), ('bahiagas'),
  ('supergasbras'), ('ultragaz'), ('copagaz'), ('liquigas'), ('copergas')
) AS p(pattern);

-- Telecomunicações (parent — plan type unknown: could be mobile, internet or cable)
-- Skipped: 'tim' (3 chars), 'oi' (2 chars)
INSERT INTO banking.transaction_rules (account_id, category_id, pattern, pattern_type, applicable_to, priority, is_system)
SELECT NULL, cat.id, p.pattern, 'CONTAINS', 'EXPENSE', 100, true
FROM (SELECT id FROM banking.transaction_categories WHERE name = 'Telecomunicações' AND is_system = true LIMIT 1) AS cat
CROSS JOIN (VALUES
  ('claro'), ('vivo'), ('algar telecom'), ('brisanet'),
  ('unifique'), ('surf telecom')
) AS p(pattern);

-- Internet (known fixed-broadband-only providers)
INSERT INTO banking.transaction_rules (account_id, category_id, pattern, pattern_type, applicable_to, priority, is_system)
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
INSERT INTO banking.transaction_rules (account_id, category_id, pattern, pattern_type, applicable_to, priority, is_system)
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
INSERT INTO banking.transaction_rules (account_id, category_id, pattern, pattern_type, applicable_to, priority, is_system)
SELECT NULL, cat.id, p.pattern, 'CONTAINS', 'EXPENSE', 100, true
FROM (SELECT id FROM banking.transaction_categories WHERE name = 'Eletrônicos' AND is_system = true LIMIT 1) AS cat
CROSS JOIN (VALUES
  ('kabum'), ('terabyte'), ('pichau'), ('fastshop')
) AS p(pattern);

-- Compras (general e-commerce)
-- Skipped: 'ponto' standalone (too short), 'wish' (common word), 'olx' (3 chars)
-- 'mercado pago' is a payment gateway but often appears as the merchant in card statements
INSERT INTO banking.transaction_rules (account_id, category_id, pattern, pattern_type, applicable_to, priority, is_system)
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
INSERT INTO banking.transaction_rules (account_id, category_id, pattern, pattern_type, applicable_to, priority, is_system)
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

INSERT INTO banking.transaction_rules (account_id, category_id, pattern, pattern_type, applicable_to, priority, is_system)
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
INSERT INTO banking.transaction_rules (account_id, category_id, pattern, pattern_type, applicable_to, priority, is_system)
SELECT NULL, cat.id, p.pattern, 'CONTAINS', a.applicable_to, 100, true
FROM (SELECT id FROM banking.transaction_categories WHERE name = 'PIX' AND applicable_to = 'ANY' AND is_system = true LIMIT 1) AS cat
CROSS JOIN (VALUES ('pix')) AS p(pattern)
CROSS JOIN (VALUES ('INCOME'), ('TRANSFER'), ('EXPENSE')) AS a(applicable_to);
