SET search_path TO banking, public;

-- ─────────────────────────────────────────────────────────────────────────────
-- Merchants table
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE banking.merchants (
    id         UUID        NOT NULL DEFAULT gen_random_uuid(),
    merchant_name TEXT NOT NULL,
    display_name TEXT NOT NULL,
    domain     TEXT,
    sector     TEXT,
    is_system  BOOLEAN     NOT NULL DEFAULT false,
    account_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_merchants PRIMARY KEY (id)
);

-- System merchants: unique name within the global catalog
CREATE UNIQUE INDEX uq_merchants_system_name
    ON banking.merchants (merchant_name)
    WHERE account_id IS NULL AND is_system = true;

-- User merchants: unique name per account
CREATE UNIQUE INDEX uq_merchants_user_name
    ON banking.merchants (account_id, merchant_name)
    WHERE account_id IS NOT NULL;

-- Fast lookup by domain (logo cache join, hydration job)
CREATE INDEX idx_merchants_domain ON banking.merchants (domain) WHERE domain IS NOT NULL;

-- Fast lookup by account (suggestion query)
CREATE INDEX idx_merchants_account_id ON banking.merchants (account_id) WHERE account_id IS NOT NULL;

-- ─────────────────────────────────────────────────────────────────────────────
-- Merchant logo cache — domain-keyed, shared across all merchants
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE banking.merchant_logo_cache (
    domain     TEXT NOT NULL,
    asset_id   UUID  NOT NULL,  -- UUID returned by the assets service
    fetched_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_merchant_logo_cache PRIMARY KEY (domain)
);

-- ─────────────────────────────────────────────────────────────────────────────
-- System merchant seed (derived from merchants-seed.csv)
-- Domains that contain a path segment (e.g. "ikea.com/br") are stored with
-- just the registrable domain so logo.dev can resolve them correctly.
-- ─────────────────────────────────────────────────────────────────────────────

INSERT INTO banking.merchants (merchant_name, display_name, domain, sector, is_system, account_id)
VALUES
  -- alimentacao
  ('carrefour', 'Carrefour', 'carrefour.com.br', 'alimentacao', true, NULL),
  ('atacadao', 'Atacadao', 'atacadao.com.br', 'alimentacao', true, NULL),
  ('assai', 'Assaí', 'assai.com.br', 'alimentacao', true, NULL),
  ('pao de acucar', 'Pão de Açúcar', 'grupopaodeacucar.com.br', 'alimentacao', true, NULL),
  ('extra', 'Extra', 'grupopaodeacucar.com.br', 'alimentacao', true, NULL),
  ('sams club', 'Sams Club', 'samsclub.com.br', 'alimentacao', true, NULL),
  ('makro', 'Makro', 'makro.com.br', 'alimentacao', true, NULL),
  ('spani', 'Spani', 'spani.com.br', 'alimentacao', true, NULL),
  ('condor', 'Condor', 'supercondor.com.br', 'alimentacao', true, NULL),
  ('muffato', 'Muffato', 'supermuffato.com.br', 'alimentacao', true, NULL),
  ('angeloni', 'Angeloni', 'angeloni.com.br', 'alimentacao', true, NULL),
  ('giassi', 'Giassi', 'giassi.com.br', 'alimentacao', true, NULL),
  ('zaffari', 'Zaffari', 'zaffari.com.br', 'alimentacao', true, NULL),
  ('prezunic', 'Prezunic', 'prezunic.com.br', 'alimentacao', true, NULL),
  ('zona sul', 'Zona Sul', 'zonasul.com.br', 'alimentacao', true, NULL),
  ('bahamas', 'Bahamas', 'superbahamas.com.br', 'alimentacao', true, NULL),
  ('bretas', 'Bretas', 'bretas.com.br', 'alimentacao', true, NULL),
  ('verdemar', 'Verdemar', 'verdemar.com.br', 'alimentacao', true, NULL),
  ('bh supermercados', 'BH Supermercados', 'supermercadosbh.com.br', 'alimentacao', true, NULL),
  ('hirota', 'Hirota', 'hirota.com.br', 'alimentacao', true, NULL),
  ('hortifruti', 'Hortifruti', 'hortifruti.com.br', 'alimentacao', true, NULL),
  ('oba hortifruti', 'Oba Hortifruti', 'obahortifruti.com.br', 'alimentacao', true, NULL),
  ('natural da terra', 'Natural Da Terra', 'naturaldaterra.com.br', 'alimentacao', true, NULL),
  ('saint marche', 'Saint Marche', 'saintmarche.com.br', 'alimentacao', true, NULL),
  ('sonda', 'Sonda', 'sonda.com.br', 'alimentacao', true, NULL),
  ('comper', 'Comper', 'comper.com.br', 'alimentacao', true, NULL),
  ('nacional supermercados', 'Nacional Supermercados', 'supernacional.com.br', 'alimentacao', true, NULL),
  -- delivery
  ('ifood', 'iFood', 'ifood.com.br', 'delivery', true, NULL),
  ('rappi', 'Rappi', 'rappi.com.br', 'delivery', true, NULL),
  ('uber eats', 'Uber Eats', 'ubereats.com', 'delivery', true, NULL),
  -- restaurante
  ('mcdonalds', 'McDonald''s', 'mcdonalds.com.br', 'restaurante', true, NULL),
  ('mc donalds', 'McDonald''s', 'mcdonalds.com.br', 'restaurante', true, NULL),
  ('burger king', 'Burger King', 'burgerking.com.br', 'restaurante', true, NULL),
  ('subway', 'Subway', 'subway.com', 'restaurante', true, NULL),
  ('kfc', 'KFC', 'kfc.com.br', 'restaurante', true, NULL),
  ('pizza hut', 'Pizza Hut', 'pizzahut.com.br', 'restaurante', true, NULL),
  ('dominos', 'Domino''s', 'dominos.com.br', 'restaurante', true, NULL),
  ('bobs', 'Bob''s', 'bobs.com.br', 'restaurante', true, NULL),
  ('giraffas', 'Giraffas', 'giraffas.com.br', 'restaurante', true, NULL),
  ('habibs', 'Habib''s', 'habibs.com.br', 'restaurante', true, NULL),
  ('spoleto', 'Spoleto', 'spoleto.com.br', 'restaurante', true, NULL),
  ('madero', 'Madero', 'restaurantemadero.com.br', 'restaurante', true, NULL),
  ('china in box', 'China In Box', 'chinainbox.com.br', 'restaurante', true, NULL),
  ('ragazzo', 'Ragazzo', 'ragazzo.com.br', 'restaurante', true, NULL),
  ('jeronimo', 'Jeronimo', 'jeronimo.com.br', 'restaurante', true, NULL),
  ('frango assado', 'Frango Assado', 'frangoassado.com.br', 'restaurante', true, NULL),
  ('outback', 'Outback', 'outback.com.br', 'restaurante', true, NULL),
  ('starbucks', 'Starbucks', 'starbucks.com.br', 'restaurante', true, NULL),
  ('oakberry', 'Oakberry', 'oakberry.com', 'restaurante', true, NULL),
  ('taco bell', 'Taco Bell', 'tacobell.com', 'restaurante', true, NULL),
  ('five guys', 'Five Guys', 'fiveguys.com', 'restaurante', true, NULL),
  ('popeyes', 'Popeyes', 'popeyes.com', 'restaurante', true, NULL),
  ('dunkin', 'Dunkin', 'dunkindonuts.com.br', 'restaurante', true, NULL),
  ('koni', 'Koni', 'koni.com.br', 'restaurante', true, NULL),
  ('baked potato', 'Baked Potato', 'bakedpotato.com.br', 'restaurante', true, NULL),
  ('rei do mate', 'Rei Do Mate', 'reidomate.com.br', 'restaurante', true, NULL),
  ('casa do pao de queijo', 'Casa do Pão de Queijo', 'casadopaodequeijo.com.br', 'restaurante', true, NULL),
  ('frans cafe', 'Fran''s Café', 'franscafe.com.br', 'restaurante', true, NULL),
  ('blend burguer', 'Blend Burguer', 'blendburguer.com.br', 'restaurante', true, NULL),
  ('coco bambu', 'Coco Bambu', 'cocobambu.com.br', 'restaurante', true, NULL),
  ('vivenda do camarao', 'Vivenda do Camarão', 'vivendadocamarao.com.br', 'restaurante', true, NULL),
  ('almanara', 'Almanara', 'almanara.com.br', 'restaurante', true, NULL),
  ('fogo de chao', 'Fogo de Chão', 'fogodechao.com', 'restaurante', true, NULL),
  ('pobre juan', 'Pobre Juan', 'pobrejuan.com.br', 'restaurante', true, NULL),
  ('gendai', 'Gendai', 'gendai.com.br', 'restaurante', true, NULL),
  ('camarada camarao', 'Camarada Camarão', 'camaradacamarao.com.br', 'restaurante', true, NULL),
  ('patroni pizza', 'Patroni Pizza', 'patronipizza.com.br', 'restaurante', true, NULL),
  ('parme', 'Parme', 'parme.com.br', 'restaurante', true, NULL),
  ('montana grill', 'Montana Grill', 'montanagrill.com.br', 'restaurante', true, NULL),
  ('divino fogao', 'Divino Fogão', 'divinofogao.com.br', 'restaurante', true, NULL),
  ('chilis', 'Chilis', 'chilis.com.br', 'restaurante', true, NULL),
  ('applebees', 'Applebees', 'applebees.com.br', 'restaurante', true, NULL),
  ('hard rock cafe', 'Hard Rock Cafe', 'hardrock.com', 'restaurante', true, NULL),
  ('sushiloko', 'Sushiloko', 'sushiloko.com.br', 'restaurante', true, NULL),
  ('temakeria', 'Temakeria', 'temakeria.com.br', 'restaurante', true, NULL),
  ('hokkai', 'Hokkai', 'hokkai.com.br', 'restaurante', true, NULL),
  ('bibi sucos', 'Bibi Sucos', 'bibisucos.com.br', 'restaurante', true, NULL),
  ('rascal', 'Ráscal', 'rascal.com.br', 'restaurante', true, NULL),
  ('ofner', 'Ofner', 'ofner.com.br', 'restaurante', true, NULL),
  ('arabia', 'Arabia', 'arabia.com.br', 'restaurante', true, NULL),
  ('barbacoa', 'Barbacoa', 'barbacoa.com.br', 'restaurante', true, NULL),
  ('rubaiyat', 'Rubaiyat', 'rubaiyat.com.br', 'restaurante', true, NULL),
  ('le pain quotidien', 'Le Pain Quotidien', 'lepainquotidien.com', 'restaurante', true, NULL),
  ('mr cheney', 'Mr Cheney', 'mrcheney.com.br', 'restaurante', true, NULL),
  ('forneria san paolo', 'Forneria San Paolo', 'forneriasanpaolo.com.br', 'restaurante', true, NULL),
  ('yogoberry', 'Yogoberry', 'yogoberry.com.br', 'restaurante', true, NULL),
  ('pitaya', 'Pitaya', 'pitaya.com.br', 'restaurante', true, NULL),
  ('bella paulista', 'Bella Paulista', 'bellapaulista.com.br', 'restaurante', true, NULL),
  ('tenda do morango', 'Tenda Do Morango', 'tendadomorango.com.br', 'restaurante', true, NULL),
  ('cookn roll', 'Cook''n Roll', 'cooknroll.com.br', 'restaurante', true, NULL),
  ('daccorsi', 'D''Accorsi', 'daccorsi.com.br', 'restaurante', true, NULL),
  ('bullguer', 'Bullguer', 'bullguer.com.br', 'restaurante', true, NULL),
  ('lanchonete da cidade', 'Lanchonete Da Cidade', 'lanchonetedacidade.com.br', 'restaurante', true, NULL),
  ('fifties', 'Fifties', 'fifties.com.br', 'restaurante', true, NULL),
  ('braz pizzaria', 'Braz Pizzaria', 'pizzariabraz.com.br', 'restaurante', true, NULL),
  ('familia mancini', 'Familia Mancini', 'familiamancini.com.br', 'restaurante', true, NULL),
  ('viena', 'Viena', 'viena.com.br', 'restaurante', true, NULL),
  ('amor aos pedacos', 'Amor aos Pedaços', 'amoraospedacos.com.br', 'restaurante', true, NULL),
  ('freddo', 'Freddo', 'freddo.com.br', 'restaurante', true, NULL),
  ('bacio di latte', 'Bacio di Latte', 'baciodilatte.com.br', 'restaurante', true, NULL),
  ('mil frutas', 'Mil Frutas', 'milfrutas.com.br', 'restaurante', true, NULL),
  ('haagen dazs', 'Häagen-Dazs', 'haagendazs.com', 'restaurante', true, NULL),
  ('hooters', 'Hooters', 'hooters.com.br', 'restaurante', true, NULL),
  ('tgi fridays', 'TGI Fridays', 'tgifridays.com.br', 'restaurante', true, NULL),
  ('pf changs', 'P.F. Chang''s', 'pfchangs.com.br', 'restaurante', true, NULL),
  ('nagayama', 'Nagayama', 'nagayama.com.br', 'restaurante', true, NULL),
  ('shoji', 'Shoji', 'shojirestaurante.com.br', 'restaurante', true, NULL),
  ('salad creations', 'Salad Creations', 'saladcreations.com.br', 'restaurante', true, NULL),
  ('copa cafe', 'Copa Café', 'copacafe.com.br', 'restaurante', true, NULL),
  ('quitandinha', 'Quitandinha', 'quitandinha.com.br', 'restaurante', true, NULL),
  ('cumbuca', 'Cumbuca', 'restaurantecumbuca.com.br', 'restaurante', true, NULL),
  ('santa massa', 'Santa Massa', 'santamassa.com.br', 'restaurante', true, NULL),
  ('nonno ruggero', 'Nonno Ruggero', 'nonnoruggero.com.br', 'restaurante', true, NULL),
  ('pizza ao quadrado', 'Pizza Ao Quadrado', 'pizzaaoquadrado.com.br', 'restaurante', true, NULL),
  ('wraps', 'Wraps', 'wrapsbrasil.com.br', 'restaurante', true, NULL),
  ('telepizza', 'Telepizza', 'telepizza.com.br', 'restaurante', true, NULL),
  ('pizzaria speranza', 'Pizzaria Speranza', 'pizzariasperanza.com.br', 'restaurante', true, NULL),
  ('bom bocado', 'Bom Bocado', 'bombocado.com.br', 'restaurante', true, NULL),
  ('paris 6', 'Paris 6', 'paris6.com.br', 'restaurante', true, NULL),
  ('le vin', 'Le Vin', 'levin.com.br', 'restaurante', true, NULL),
  ('suplicy', 'Suplicy', 'suplicy.com.br', 'restaurante', true, NULL),
  ('havanna', 'Havanna', 'havanna.com.br', 'restaurante', true, NULL),
  ('confeitaria colombo', 'Confeitaria Colombo', 'confeitariacolombo.com.br', 'restaurante', true, NULL),
  ('cacau show', 'Cacau Show', 'cacaushow.com.br', 'restaurante', true, NULL),
  ('kopenhagen', 'Kopenhagen', 'kopenhagen.com.br', 'restaurante', true, NULL),
  ('mundo verde', 'Mundo Verde', 'mundoverde.com.br', 'restaurante', true, NULL),
  ('crepe mania', 'Crepe Mania', 'crepemania.com.br', 'restaurante', true, NULL),
  ('porcao', 'Porcão', 'porcao.com.br', 'restaurante', true, NULL),
  ('rafain', 'Rafain', 'rafain.com.br', 'restaurante', true, NULL),
  ('ribs', 'Ribs', 'ribs.com.br', 'restaurante', true, NULL),
  ('tordesilhas', 'Tordesilhas', 'tordesilhas.com.br', 'restaurante', true, NULL),
  ('mocoto', 'Mocotó', 'mocoto.com.br', 'restaurante', true, NULL),
  ('spot', 'Spot', 'restaurantespot.com.br', 'restaurante', true, NULL),
  ('mani', 'Mani', 'manirestaurante.com.br', 'restaurante', true, NULL),
  ('paris beirut', 'Paris Beirut', 'parisbeirut.com.br', 'restaurante', true, NULL),
  ('sushi leblon', 'Sushi Leblon', 'sushileblon.com.br', 'restaurante', true, NULL),
  ('aoyama', 'Aoyama', 'aoyama.com.br', 'restaurante', true, NULL),
  ('bubba gump', 'Bubba Gump', 'bubbagump.com', 'restaurante', true, NULL),
  ('roasty', 'Roasty', 'roasty.com.br', 'restaurante', true, NULL),
  -- transporte
  ('uber', 'Uber', 'uber.com', 'transporte', true, NULL),
  ('99', '99', '99app.com', 'transporte', true, NULL),
  ('indrive', 'Indrive', 'indrive.com', 'transporte', true, NULL),
  ('cabify', 'Cabify', 'cabify.com', 'transporte', true, NULL),
  ('ipiranga', 'Ipiranga', 'ipiranga.com.br', 'transporte', true, NULL),
  ('shell', 'Shell', 'shell.com.br', 'transporte', true, NULL),
  ('br distribuidora', 'BR Distribuidora', 'br.com.br', 'transporte', true, NULL),
  ('ale combustiveis', 'ALE Combustíveis', 'alecombustiveis.com.br', 'transporte', true, NULL),
  ('vibra energia', 'Vibra Energia', 'vibraenergia.com.br', 'transporte', true, NULL),
  ('sem parar', 'Sem Parar', 'semparar.com.br', 'transporte', true, NULL),
  ('conectcar', 'Conectcar', 'conectcar.com.br', 'transporte', true, NULL),
  ('veloe', 'Veloe', 'veloe.com.br', 'transporte', true, NULL),
  ('estapar', 'Estapar', 'estapar.com.br', 'transporte', true, NULL),
  ('indigo', 'Indigo', 'indigoneo.com.br', 'transporte', true, NULL),
  ('localiza', 'Localiza', 'localiza.com', 'transporte', true, NULL),
  ('movida', 'Movida', 'movida.com.br', 'transporte', true, NULL),
  ('unidas', 'Unidas', 'unidas.com.br', 'transporte', true, NULL),
  ('hertz', 'Hertz', 'hertz.com.br', 'transporte', true, NULL),
  ('avis', 'Avis', 'avis.com.br', 'transporte', true, NULL),
  ('budget', 'Budget', 'budget.com.br', 'transporte', true, NULL),
  ('sptrans', 'SPTrans', 'sptrans.com.br', 'transporte', true, NULL),
  ('metro sp', 'Metrô SP', 'metro.sp.gov.br', 'transporte', true, NULL),
  ('cptm', 'CPTM', 'cptm.sp.gov.br', 'transporte', true, NULL),
  ('supervia', 'Supervia', 'supervia.com.br', 'transporte', true, NULL),
  ('metro rio', 'Metro Rio', 'metrorio.com.br', 'transporte', true, NULL),
  ('buser', 'Buser', 'buser.com.br', 'transporte', true, NULL),
  ('flixbus', 'Flixbus', 'flixbus.com.br', 'transporte', true, NULL),
  ('itapemirim', 'Itapemirim', 'itapemirim.com.br', 'transporte', true, NULL),
  ('cometa', 'Cometa', 'viacao-cometa.com.br', 'transporte', true, NULL),
  ('raizen', 'Raizen', 'raizen.com.br', 'transporte', true, NULL),
  ('texaco', 'Texaco', 'texaco.com.br', 'transporte', true, NULL),
  ('via facil', 'Via Facil', 'viafacil.com.br', 'transporte', true, NULL),
  ('ecovias', 'Ecovias', 'ecovias.com.br', 'transporte', true, NULL),
  ('multipark', 'Multipark', 'multipark.com.br', 'transporte', true, NULL),
  ('riocard', 'Riocard', 'riocard.com.br', 'transporte', true, NULL),
  ('vlt rio', 'VLT Rio', 'vltrio.com.br', 'transporte', true, NULL),
  ('trensurb', 'Trensurb', 'trensurb.gov.br', 'transporte', true, NULL),
  ('urbs', 'Urbs', 'urbs.curitiba.pr.gov.br', 'transporte', true, NULL),
  ('otimo', 'Ótimo', 'otimo.com.br', 'transporte', true, NULL),
  ('tembici', 'Tembici', 'tembici.com.br', 'transporte', true, NULL),
  ('grow', 'Grow', 'grow.com.br', 'transporte', true, NULL),
  ('real expresso', 'Real Expresso', 'realexpresso.com.br', 'transporte', true, NULL),
  ('util', 'Util', 'util.com.br', 'transporte', true, NULL),
  ('1001', '1001', 'autoviacao1001.com.br', 'transporte', true, NULL),
  ('gontijo', 'Gontijo', 'gontijo-turismo.com.br', 'transporte', true, NULL),
  ('penha', 'Penha', 'viacaopenha.com.br', 'transporte', true, NULL),
  ('pluma', 'Pluma', 'pluma.com.br', 'transporte', true, NULL),
  ('catarinense', 'Catarinense', 'catarinense.net', 'transporte', true, NULL),
  -- streaming
  ('netflix', 'Netflix', 'netflix.com', 'streaming', true, NULL),
  ('disney+', 'Disney+', 'disneyplus.com', 'streaming', true, NULL),
  ('disney plus', 'Disney Plus', 'disneyplus.com', 'streaming', true, NULL),
  ('amazon prime', 'Amazon Prime', 'primevideo.com', 'streaming', true, NULL),
  ('prime video', 'Prime Video', 'primevideo.com', 'streaming', true, NULL),
  ('hbo max', 'Hbo Max', 'max.com', 'streaming', true, NULL),
  ('max', 'Max', 'max.com', 'streaming', true, NULL),
  ('globoplay', 'Globoplay', 'globoplay.globo.com', 'streaming', true, NULL),
  ('paramount+', 'Paramount+', 'paramountplus.com', 'streaming', true, NULL),
  ('paramount plus', 'Paramount Plus', 'paramountplus.com', 'streaming', true, NULL),
  ('apple tv', 'Apple Tv', 'apple.com', 'streaming', true, NULL),
  ('youtube premium', 'Youtube Premium', 'youtube.com', 'streaming', true, NULL),
  ('crunchyroll', 'Crunchyroll', 'crunchyroll.com', 'streaming', true, NULL),
  ('telecine', 'Telecine', 'telecine.com.br', 'streaming', true, NULL),
  ('looke', 'Looke', 'looke.com.br', 'streaming', true, NULL),
  ('mubi', 'Mubi', 'mubi.com', 'streaming', true, NULL),
  ('spotify', 'Spotify', 'spotify.com', 'streaming', true, NULL),
  ('deezer', 'Deezer', 'deezer.com', 'streaming', true, NULL),
  ('apple music', 'Apple Music', 'apple.com', 'streaming', true, NULL),
  ('amazon music', 'Amazon Music', 'music.amazon.com', 'streaming', true, NULL),
  ('tidal', 'Tidal', 'tidal.com', 'streaming', true, NULL),
  ('audible', 'Audible', 'audible.com', 'streaming', true, NULL),
  ('storytel', 'Storytel', 'storytel.com', 'streaming', true, NULL),
  ('sky', 'Sky', 'sky.com.br', 'streaming', true, NULL),
  ('premiere', 'Premiere', 'premierfc.globo.com', 'streaming', true, NULL),
  ('dazn', 'Dazn', 'dazn.com', 'streaming', true, NULL),
  ('cinemark', 'Cinemark', 'cinemark.com.br', 'streaming', true, NULL),
  ('cinepolis', 'Cinepolis', 'cinepolis.com.br', 'streaming', true, NULL),
  ('uci cinemas', 'Uci Cinemas', 'ucicinemas.com.br', 'streaming', true, NULL),
  ('cinesystem', 'Cinesystem', 'cinesystem.com.br', 'streaming', true, NULL),
  ('kinoplex', 'Kinoplex', 'kinoplex.com.br', 'streaming', true, NULL),
  ('ingresso.com', 'Ingresso.com', 'ingresso.com', 'streaming', true, NULL),
  ('sympla', 'Sympla', 'sympla.com.br', 'streaming', true, NULL),
  ('eventbrite', 'Eventbrite', 'eventbrite.com.br', 'streaming', true, NULL),
  ('ticketmaster', 'Ticketmaster', 'ticketmaster.com.br', 'streaming', true, NULL),
  -- saude
  ('drogasil', 'Drogasil', 'drogasil.com.br', 'saude', true, NULL),
  ('droga raia', 'Droga Raia', 'drogaraia.com.br', 'saude', true, NULL),
  ('drogaria sao paulo', 'Drogaria Sao Paulo', 'drogariasaopaulo.com.br', 'saude', true, NULL),
  ('ultrafarma', 'Ultrafarma', 'ultrafarma.com.br', 'saude', true, NULL),
  ('pague menos', 'Pague Menos', 'paguemenos.com.br', 'saude', true, NULL),
  ('panvel', 'Panvel', 'panvel.com.br', 'saude', true, NULL),
  ('nissei', 'Nissei', 'nissei.com.br', 'saude', true, NULL),
  ('extrafarma', 'Extrafarma', 'extrafarma.com.br', 'saude', true, NULL),
  ('drogal', 'Drogal', 'drogal.com.br', 'saude', true, NULL),
  ('araujo', 'Araujo', 'araujo.com.br', 'saude', true, NULL),
  ('dpsp', 'Dpsp', 'dpsp.com.br', 'saude', true, NULL),
  ('unimed', 'Unimed', 'unimed.com.br', 'saude', true, NULL),
  ('amil', 'Amil', 'amil.com.br', 'saude', true, NULL),
  ('bradesco saude', 'Bradesco Saude', 'bradescosaude.com.br', 'saude', true, NULL),
  ('sulamerica saude', 'Sulamerica Saude', 'sulamerica.com.br', 'saude', true, NULL),
  ('hapvida', 'Hapvida', 'hapvida.com.br', 'saude', true, NULL),
  ('notre dame intermedica', 'Notre Dame Intermedica', 'gndi.com.br', 'saude', true, NULL),
  ('prevent senior', 'Prevent Senior', 'preventsenior.com.br', 'saude', true, NULL),
  ('golden cross', 'Golden Cross', 'goldencross.com.br', 'saude', true, NULL),
  ('fleury', 'Fleury', 'fleury.com.br', 'saude', true, NULL),
  ('dasa', 'Dasa', 'dasa.com.br', 'saude', true, NULL),
  ('hermes pardini', 'Hermes Pardini', 'hermespardini.com.br', 'saude', true, NULL),
  ('sabin', 'Sabin', 'sabin.com.br', 'saude', true, NULL),
  ('lavoisier', 'Lavoisier', 'lavoisier.com.br', 'saude', true, NULL),
  ('alta exames', 'Alta Exames', 'altaexames.com.br', 'saude', true, NULL),
  ('einstein', 'Einstein', 'einstein.br', 'saude', true, NULL),
  ('sirio libanes', 'Sirio Libanes', 'hospitalsiriolibanes.org.br', 'saude', true, NULL),
  ('rede d''or', 'Rede D''or', 'rededor.com.br', 'saude', true, NULL),
  ('bp beneficencia portuguesa', 'Bp Beneficencia Portuguesa', 'bp.org.br', 'saude', true, NULL),
  ('odontoprev', 'Odontoprev', 'odontoprev.com.br', 'saude', true, NULL),
  ('uniodonto', 'Uniodonto', 'uniodonto.com.br', 'saude', true, NULL),
  ('dr. consulta', 'Dr. Consulta', 'drconsulta.com', 'saude', true, NULL),
  ('doctoralia', 'Doctoralia', 'doctoralia.com.br', 'saude', true, NULL),
  ('conexa saude', 'Conexa Saude', 'conexasaude.com.br', 'saude', true, NULL),
  ('smartfit', 'Smartfit', 'smartfit.com.br', 'saude', true, NULL),
  ('bodytech', 'Bodytech', 'bodytech.com.br', 'saude', true, NULL),
  ('bio ritmo', 'Bio Ritmo', 'bioritmoacademia.com.br', 'saude', true, NULL),
  ('bluefit', 'Bluefit', 'bluefit.com.br', 'saude', true, NULL),
  ('golds gym', 'Golds Gym', 'goldsgym.com.br', 'saude', true, NULL),
  ('gympass', 'Gympass', 'gympass.com', 'saude', true, NULL),
  ('wellhub', 'Wellhub', 'wellhub.com', 'saude', true, NULL),
  ('drogaria venancio', 'Drogaria Venancio', 'drogariavenancio.com.br', 'saude', true, NULL),
  ('farma conde', 'Farma Conde', 'farmaconde.com.br', 'saude', true, NULL),
  ('onofre', 'Onofre', 'onofre.com.br', 'saude', true, NULL),
  ('omint', 'Omint', 'omint.com.br', 'saude', true, NULL),
  ('assim saude', 'Assim Saude', 'assimdigital.com.br', 'saude', true, NULL),
  ('porto seguro saude', 'Porto Seguro Saude', 'portoseguro.com.br', 'saude', true, NULL),
  ('cassi', 'Cassi', 'cassi.com.br', 'saude', true, NULL),
  ('geap', 'Geap', 'geap.com.br', 'saude', true, NULL),
  ('alice saude', 'Alice Saude', 'alice.com.br', 'saude', true, NULL),
  ('sami saude', 'Sami Saude', 'samisaude.com.br', 'saude', true, NULL),
  ('hcor', 'Hcor', 'hcor.com.br', 'saude', true, NULL),
  ('moinhos de vento', 'Moinhos De Vento', 'hmv.org.br', 'saude', true, NULL),
  ('oswaldo cruz', 'Oswaldo Cruz', 'haoc.com.br', 'saude', true, NULL),
  ('hospital nove de julho', 'Hospital Nove De Julho', 'h9j.com.br', 'saude', true, NULL),
  ('psicologia viva', 'Psicologia Viva', 'psicologiaviva.com.br', 'saude', true, NULL),
  ('zenklub', 'Zenklub', 'zenklub.com', 'saude', true, NULL),
  ('vittude', 'Vittude', 'vittude.com', 'saude', true, NULL),
  ('totalpass', 'Totalpass', 'totalpass.com.br', 'saude', true, NULL),
  ('cia athletica', 'Cia Athletica', 'ciaathletica.com.br', 'saude', true, NULL),
  ('oticas carol', 'Oticas Carol', 'oticascarol.com.br', 'saude', true, NULL),
  ('oticas diniz', 'Oticas Diniz', 'oticasdiniz.com.br', 'saude', true, NULL),
  ('oticas penha', 'Oticas Penha', 'oticaspenha.com.br', 'saude', true, NULL),
  ('grandvision', 'Grandvision', 'grandvision.com.br', 'saude', true, NULL),
  ('formula e acao', 'Formula E Acao', 'formulaeacao.com.br', 'saude', true, NULL),
  -- educacao
  ('duolingo', 'Duolingo', 'duolingo.com', 'educacao', true, NULL),
  ('udemy', 'Udemy', 'udemy.com', 'educacao', true, NULL),
  ('coursera', 'Coursera', 'coursera.org', 'educacao', true, NULL),
  ('alura', 'Alura', 'alura.com.br', 'educacao', true, NULL),
  ('descomplica', 'Descomplica', 'descomplica.com.br', 'educacao', true, NULL),
  ('hotmart', 'Hotmart', 'hotmart.com', 'educacao', true, NULL),
  ('eduzz', 'Eduzz', 'eduzz.com', 'educacao', true, NULL),
  ('kiwify', 'Kiwify', 'kiwify.com.br', 'educacao', true, NULL),
  ('rocketseat', 'Rocketseat', 'rocketseat.com.br', 'educacao', true, NULL),
  ('dio', 'Dio', 'dio.me', 'educacao', true, NULL),
  ('pluralsight', 'Pluralsight', 'pluralsight.com', 'educacao', true, NULL),
  ('skillshare', 'Skillshare', 'skillshare.com', 'educacao', true, NULL),
  ('linkedin learning', 'Linkedin Learning', 'linkedin.com', 'educacao', true, NULL),
  ('estacio', 'Estacio', 'estacio.br', 'educacao', true, NULL),
  ('anhanguera', 'Anhanguera', 'anhanguera.com', 'educacao', true, NULL),
  ('unopar', 'Unopar', 'unopar.com.br', 'educacao', true, NULL),
  ('unip', 'Unip', 'unip.br', 'educacao', true, NULL),
  ('fgv', 'Fgv', 'fgv.br', 'educacao', true, NULL),
  ('insper', 'Insper', 'insper.edu.br', 'educacao', true, NULL),
  ('espm', 'Espm', 'espm.br', 'educacao', true, NULL),
  ('mackenzie', 'Mackenzie', 'mackenzie.br', 'educacao', true, NULL),
  ('faap', 'Faap', 'faap.br', 'educacao', true, NULL),
  ('ibmec', 'Ibmec', 'ibmec.br', 'educacao', true, NULL),
  ('wizard', 'Wizard', 'wizard.com.br', 'educacao', true, NULL),
  ('fisk', 'Fisk', 'fisk.com.br', 'educacao', true, NULL),
  ('ccaa', 'Ccaa', 'ccaa.com.br', 'educacao', true, NULL),
  ('cna', 'Cna', 'cna.com.br', 'educacao', true, NULL),
  ('cultura inglesa', 'Cultura Inglesa', 'culturainglesa.com.br', 'educacao', true, NULL),
  ('berlitz', 'Berlitz', 'berlitz.com.br', 'educacao', true, NULL),
  ('yazigi', 'Yazigi', 'yazigi.com.br', 'educacao', true, NULL),
  ('senai', 'Senai', 'senai.br', 'educacao', true, NULL),
  ('senac', 'Senac', 'senac.br', 'educacao', true, NULL),
  ('sebrae', 'Sebrae', 'sebrae.com.br', 'educacao', true, NULL),
  ('saraiva', 'Saraiva', 'saraiva.com.br', 'educacao', true, NULL),
  ('livraria cultura', 'Livraria Cultura', 'livrariacultura.com.br', 'educacao', true, NULL),
  ('kalunga', 'Kalunga', 'kalunga.com.br', 'educacao', true, NULL),
  ('maple bear', 'Maple Bear', 'maplebear.com.br', 'educacao', true, NULL),
  ('coc', 'Coc', 'coc.com.br', 'educacao', true, NULL),
  ('anglo', 'Anglo', 'colegioeanglo.com.br', 'educacao', true, NULL),
  ('objetivo', 'Objetivo', 'objetivo.br', 'educacao', true, NULL),
  ('dante alighieri', 'Dante Alighieri', 'dante.com.br', 'educacao', true, NULL),
  ('santa cruz', 'Santa Cruz', 'colegiosantacruz.com.br', 'educacao', true, NULL),
  ('bandeirantes', 'Bandeirantes', 'bandeirantes.com.br', 'educacao', true, NULL),
  ('etapa', 'Etapa', 'etapa.com.br', 'educacao', true, NULL),
  ('marista', 'Marista', 'marista.edu.br', 'educacao', true, NULL),
  ('salesiano', 'Salesiano', 'salesiano.edu.br', 'educacao', true, NULL),
  ('bernoulli', 'Bernoulli', 'bernoulli.com.br', 'educacao', true, NULL),
  ('positivo', 'Positivo', 'colegiopositivo.com.br', 'educacao', true, NULL),
  ('pitagoras', 'Pitagoras', 'pitagoras.com.br', 'educacao', true, NULL),
  ('poliedro', 'Poliedro', 'sistemapoliedro.com.br', 'educacao', true, NULL),
  ('eleva', 'Eleva', 'elevaeducacao.com.br', 'educacao', true, NULL),
  ('ph cursinho', 'Ph Cursinho', 'ph.com.br', 'educacao', true, NULL),
  ('cogna', 'Cogna', 'cogna.com.br', 'educacao', true, NULL),
  ('cruzeiro do sul', 'Cruzeiro Do Sul', 'cruzeirodosul.edu.br', 'educacao', true, NULL),
  ('sao judas', 'Sao Judas', 'saojudas.br', 'educacao', true, NULL),
  ('belas artes', 'Belas Artes', 'belasartes.br', 'educacao', true, NULL),
  ('puc', 'Puc', 'pucsp.br', 'educacao', true, NULL),
  ('uniseb', 'Uniseb', 'uniseb.com.br', 'educacao', true, NULL),
  ('ampli', 'Ampli', 'ampli.com.br', 'educacao', true, NULL),
  ('domestika', 'Domestika', 'domestika.org', 'educacao', true, NULL),
  ('masterclass', 'Masterclass', 'masterclass.com', 'educacao', true, NULL),
  ('babbel', 'Babbel', 'babbel.com', 'educacao', true, NULL),
  ('open english', 'Open English', 'openenglish.com', 'educacao', true, NULL),
  ('rosetta stone', 'Rosetta Stone', 'rosettastone.com', 'educacao', true, NULL),
  ('empiricus', 'Empiricus', 'empiricus.com.br', 'educacao', true, NULL),
  ('hashtag treinamentos', 'Hashtag Treinamentos', 'hashtagtreinamentos.com.br', 'educacao', true, NULL),
  -- utilities
  ('enel', 'Enel', 'enel.com.br', 'utilities', true, NULL),
  ('eletropaulo', 'Eletropaulo', 'enel.com.br', 'utilities', true, NULL),
  ('cemig', 'Cemig', 'cemig.com.br', 'utilities', true, NULL),
  ('copel', 'Copel', 'copel.com', 'utilities', true, NULL),
  ('cpfl', 'Cpfl', 'cpfl.com.br', 'utilities', true, NULL),
  ('light', 'Light', 'light.com.br', 'utilities', true, NULL),
  ('energisa', 'Energisa', 'energisa.com.br', 'utilities', true, NULL),
  ('celesc', 'Celesc', 'celesc.com.br', 'utilities', true, NULL),
  ('neoenergia', 'Neoenergia', 'neoenergia.com', 'utilities', true, NULL),
  ('coelba', 'Coelba', 'coelba.com.br', 'utilities', true, NULL),
  ('elektro', 'Elektro', 'elektro.com.br', 'utilities', true, NULL),
  ('edp', 'Edp', 'edp.com.br', 'utilities', true, NULL),
  ('ceee', 'Ceee', 'ceee.com.br', 'utilities', true, NULL),
  ('equatorial', 'Equatorial', 'equatorialenergia.com.br', 'utilities', true, NULL),
  ('sabesp', 'Sabesp', 'sabesp.com.br', 'utilities', true, NULL),
  ('cedae', 'Cedae', 'cedae.com.br', 'utilities', true, NULL),
  ('sanepar', 'Sanepar', 'sanepar.com.br', 'utilities', true, NULL),
  ('embasa', 'Embasa', 'embasa.com.br', 'utilities', true, NULL),
  ('caesb', 'Caesb', 'caesb.df.gov.br', 'utilities', true, NULL),
  ('copasa', 'Copasa', 'copasa.com.br', 'utilities', true, NULL),
  ('cagece', 'Cagece', 'cagece.com.br', 'utilities', true, NULL),
  ('corsan', 'Corsan', 'corsan.com.br', 'utilities', true, NULL),
  ('casan', 'Casan', 'casan.com.br', 'utilities', true, NULL),
  ('saneago', 'Saneago', 'saneago.com.br', 'utilities', true, NULL),
  ('aegea', 'Aegea', 'aegea.com.br', 'utilities', true, NULL),
  ('brk ambiental', 'Brk Ambiental', 'brkambiental.com.br', 'utilities', true, NULL),
  ('comgas', 'Comgas', 'comgas.com.br', 'utilities', true, NULL),
  ('ceg', 'Ceg', 'ceg.com.br', 'utilities', true, NULL),
  ('gasmig', 'Gasmig', 'gasmig.com.br', 'utilities', true, NULL),
  ('naturgy', 'Naturgy', 'naturgy.com.br', 'utilities', true, NULL),
  ('bahiagas', 'Bahiagas', 'bahiagas.com.br', 'utilities', true, NULL),
  ('claro', 'Claro', 'claro.com.br', 'utilities', true, NULL),
  ('vivo', 'Vivo', 'vivo.com.br', 'utilities', true, NULL),
  ('tim', 'Tim', 'tim.com.br', 'utilities', true, NULL),
  ('oi', 'Oi', 'oi.com.br', 'utilities', true, NULL),
  ('algar telecom', 'Algar Telecom', 'algartelecom.com.br', 'utilities', true, NULL),
  ('brisanet', 'Brisanet', 'brisanet.com.br', 'utilities', true, NULL),
  ('celpe', 'Celpe', 'celpe.com.br', 'utilities', true, NULL),
  ('cosern', 'Cosern', 'cosern.com.br', 'utilities', true, NULL),
  ('coelce', 'Coelce', 'coelce.com.br', 'utilities', true, NULL),
  ('celg', 'Celg', 'celg.com.br', 'utilities', true, NULL),
  ('rge', 'Rge', 'rge.com.br', 'utilities', true, NULL),
  ('caern', 'Caern', 'caern.com.br', 'utilities', true, NULL),
  ('deso', 'Deso', 'deso.com.br', 'utilities', true, NULL),
  ('sanesul', 'Sanesul', 'sanesul.com.br', 'utilities', true, NULL),
  ('caema', 'Caema', 'caema.com.br', 'utilities', true, NULL),
  ('cosama', 'Cosama', 'cosama.com.br', 'utilities', true, NULL),
  ('agespisa', 'Agespisa', 'agespisa.com.br', 'utilities', true, NULL),
  ('scgas', 'Scgas', 'scgas.com.br', 'utilities', true, NULL),
  ('copergas', 'Copergas', 'copergas.com.br', 'utilities', true, NULL),
  ('algas', 'Algas', 'algas.com.br', 'utilities', true, NULL),
  ('supergasbras', 'Supergasbras', 'supergasbras.com.br', 'utilities', true, NULL),
  ('ultragaz', 'Ultragaz', 'ultragaz.com.br', 'utilities', true, NULL),
  ('copagaz', 'Copagaz', 'copagaz.com.br', 'utilities', true, NULL),
  ('liquigas', 'Liquigas', 'liquigas.com.br', 'utilities', true, NULL),
  ('starlink', 'Starlink', 'starlink.com', 'utilities', true, NULL),
  ('unifique', 'Unifique', 'unifique.com.br', 'utilities', true, NULL),
  ('desktop', 'Desktop', 'desktop.com.br', 'utilities', true, NULL),
  ('mob telecom', 'Mob Telecom', 'mobtelecom.com.br', 'utilities', true, NULL),
  ('surf telecom', 'Surf Telecom', 'surftelecom.com.br', 'utilities', true, NULL),
  ('porto seguro seguros', 'Porto Seguro Seguros', 'portoseguro.com.br', 'utilities', true, NULL),
  ('bradesco seguros', 'Bradesco Seguros', 'bradescoseguros.com.br', 'utilities', true, NULL),
  ('mapfre', 'Mapfre', 'mapfre.com.br', 'utilities', true, NULL),
  ('allianz', 'Allianz', 'allianz.com.br', 'utilities', true, NULL),
  ('liberty seguros', 'Liberty Seguros', 'libertyseguros.com.br', 'utilities', true, NULL),
  ('hdi seguros', 'Hdi Seguros', 'hdiseguros.com.br', 'utilities', true, NULL),
  ('tokio marine', 'Tokio Marine', 'tokiomarine.com.br', 'utilities', true, NULL),
  ('zurich', 'Zurich', 'zurich.com.br', 'utilities', true, NULL),
  ('sompo', 'Sompo', 'sompo.com.br', 'utilities', true, NULL),
  -- ecommerce
  ('amazon', 'Amazon', 'amazon.com.br', 'ecommerce', true, NULL),
  ('mercado livre', 'Mercado Livre', 'mercadolivre.com.br', 'ecommerce', true, NULL),
  ('mercadolivre', 'Mercadolivre', 'mercadolivre.com.br', 'ecommerce', true, NULL),
  ('mercado pago', 'Mercado Pago', 'mercadopago.com.br', 'ecommerce', true, NULL),
  ('mercadopago', 'Mercadopago', 'mercadopago.com.br', 'ecommerce', true, NULL),
  ('shopee', 'Shopee', 'shopee.com.br', 'ecommerce', true, NULL),
  ('americanas', 'Americanas', 'americanas.com.br', 'ecommerce', true, NULL),
  ('submarino', 'Submarino', 'submarino.com.br', 'ecommerce', true, NULL),
  ('shoptime', 'Shoptime', 'shoptime.com.br', 'ecommerce', true, NULL),
  ('magazine luiza', 'Magazine Luiza', 'magazineluiza.com.br', 'ecommerce', true, NULL),
  ('magalu', 'Magalu', 'magazineluiza.com.br', 'ecommerce', true, NULL),
  ('casas bahia', 'Casas Bahia', 'casasbahia.com.br', 'ecommerce', true, NULL),
  ('ponto', 'Ponto', 'pontofrio.com.br', 'ecommerce', true, NULL),
  ('ponto frio', 'Ponto Frio', 'pontofrio.com.br', 'ecommerce', true, NULL),
  ('netshoes', 'Netshoes', 'netshoes.com.br', 'ecommerce', true, NULL),
  ('zattini', 'Zattini', 'zattini.com.br', 'ecommerce', true, NULL),
  ('dafiti', 'Dafiti', 'dafiti.com.br', 'ecommerce', true, NULL),
  ('shein', 'Shein', 'shein.com', 'ecommerce', true, NULL),
  ('temu', 'Temu', 'temu.com', 'ecommerce', true, NULL),
  ('aliexpress', 'Aliexpress', 'aliexpress.com', 'ecommerce', true, NULL),
  ('wish', 'Wish', 'wish.com', 'ecommerce', true, NULL),
  ('ebay', 'Ebay', 'ebay.com', 'ecommerce', true, NULL),
  ('etsy', 'Etsy', 'etsy.com', 'ecommerce', true, NULL),
  ('enjoei', 'Enjoei', 'enjoei.com.br', 'ecommerce', true, NULL),
  ('olx', 'Olx', 'olx.com.br', 'ecommerce', true, NULL),
  ('elo7', 'Elo7', 'elo7.com.br', 'ecommerce', true, NULL),
  ('fastshop', 'Fastshop', 'fastshop.com.br', 'ecommerce', true, NULL),
  ('kabum', 'Kabum', 'kabum.com.br', 'ecommerce', true, NULL),
  ('terabyte', 'Terabyte', 'terabyteshop.com.br', 'ecommerce', true, NULL),
  ('pichau', 'Pichau', 'pichau.com.br', 'ecommerce', true, NULL),
  ('beleza na web', 'Beleza Na Web', 'belezanaweb.com.br', 'ecommerce', true, NULL),
  ('epoca cosmeticos', 'Epoca Cosmeticos', 'epocacosmeticos.com.br', 'ecommerce', true, NULL),
  ('iherb', 'Iherb', 'iherb.com', 'ecommerce', true, NULL),
  ('natue', 'Natue', 'natue.com.br', 'ecommerce', true, NULL),
  -- moda
  ('renner', 'Renner', 'lojasrenner.com.br', 'moda', true, NULL),
  ('cea', 'Cea', 'cea.com.br', 'moda', true, NULL),
  ('riachuelo', 'Riachuelo', 'riachuelo.com.br', 'moda', true, NULL),
  ('zara', 'Zara', 'zara.com', 'moda', true, NULL),
  ('hm', 'Hm', 'hm.com', 'moda', true, NULL),
  ('marisa', 'Marisa', 'marisa.com.br', 'moda', true, NULL),
  ('hering', 'Hering', 'hering.com.br', 'moda', true, NULL),
  ('farm', 'Farm', 'farmrio.com', 'moda', true, NULL),
  ('animale', 'Animale', 'animale.com.br', 'moda', true, NULL),
  ('le lis blanc', 'Le Lis Blanc', 'lelisblanc.com.br', 'moda', true, NULL),
  ('mixed', 'Mixed', 'mixed.com.br', 'moda', true, NULL),
  ('colcci', 'Colcci', 'colcci.com.br', 'moda', true, NULL),
  ('osklen', 'Osklen', 'osklen.com', 'moda', true, NULL),
  ('ellus', 'Ellus', 'ellus.com.br', 'moda', true, NULL),
  ('forum', 'Forum', 'forum.com.br', 'moda', true, NULL),
  ('richards', 'Richards', 'richards.com.br', 'moda', true, NULL),
  ('reserva', 'Reserva', 'reserva.com.br', 'moda', true, NULL),
  ('malwee', 'Malwee', 'malwee.com.br', 'moda', true, NULL),
  ('lupo', 'Lupo', 'lupo.com.br', 'moda', true, NULL),
  ('nike', 'Nike', 'nike.com.br', 'moda', true, NULL),
  ('adidas', 'Adidas', 'adidas.com.br', 'moda', true, NULL),
  ('puma', 'Puma', 'puma.com', 'moda', true, NULL),
  ('new balance', 'New Balance', 'newbalance.com.br', 'moda', true, NULL),
  ('under armour', 'Under Armour', 'underarmour.com', 'moda', true, NULL),
  ('vans', 'Vans', 'vans.com', 'moda', true, NULL),
  ('converse', 'Converse', 'converse.com', 'moda', true, NULL),
  ('calvin klein', 'Calvin Klein', 'calvinklein.com.br', 'moda', true, NULL),
  ('tommy hilfiger', 'Tommy Hilfiger', 'tommy.com', 'moda', true, NULL),
  ('lacoste', 'Lacoste', 'lacoste.com', 'moda', true, NULL),
  ('ralph lauren', 'Ralph Lauren', 'ralphlauren.com', 'moda', true, NULL),
  ('levi''s', 'Levi''s', 'levi.com', 'moda', true, NULL),
  ('guess', 'Guess', 'guess.com', 'moda', true, NULL),
  ('gap', 'Gap', 'gap.com', 'moda', true, NULL),
  ('arezzo', 'Arezzo', 'arezzo.com.br', 'moda', true, NULL),
  ('schutz', 'Schutz', 'schutz.com.br', 'moda', true, NULL),
  ('anacapri', 'Anacapri', 'anacapri.com.br', 'moda', true, NULL),
  ('havaianas', 'Havaianas', 'havaianas.com', 'moda', true, NULL),
  ('melissa', 'Melissa', 'melissa.com.br', 'moda', true, NULL),
  ('piccadilly', 'Piccadilly', 'piccadilly.com.br', 'moda', true, NULL),
  ('ramarim', 'Ramarim', 'ramarim.com.br', 'moda', true, NULL),
  ('santa lolla', 'Santa Lolla', 'santalolla.com.br', 'moda', true, NULL),
  ('via uno', 'Via Uno', 'viauno.com.br', 'moda', true, NULL),
  ('dumond', 'Dumond', 'dumond.com.br', 'moda', true, NULL),
  ('cia maritima', 'Cia Maritima', 'ciamaritima.com.br', 'moda', true, NULL),
  ('blue man', 'Blue Man', 'blueman.com.br', 'moda', true, NULL),
  ('salinas', 'Salinas', 'salinasswimwear.com.br', 'moda', true, NULL),
  ('chilli beans', 'Chilli Beans', 'chillibeans.com.br', 'moda', true, NULL),
  ('oakley', 'Oakley', 'oakley.com', 'moda', true, NULL),
  ('ray-ban', 'Ray-ban', 'ray-ban.com', 'moda', true, NULL),
  -- financas
  ('nubank', 'Nubank', 'nubank.com.br', 'financas', true, NULL),
  ('itau', 'Itau', 'itau.com.br', 'financas', true, NULL),
  ('bradesco', 'Bradesco', 'bradesco.com.br', 'financas', true, NULL),
  ('santander', 'Santander', 'santander.com.br', 'financas', true, NULL),
  ('banco do brasil', 'Banco Do Brasil', 'bb.com.br', 'financas', true, NULL),
  ('caixa', 'Caixa', 'caixa.gov.br', 'financas', true, NULL),
  ('btg pactual', 'Btg Pactual', 'btgpactual.com', 'financas', true, NULL),
  ('xp investimentos', 'Xp Investimentos', 'xp.com.br', 'financas', true, NULL),
  ('inter', 'Inter', 'bancointer.com.br', 'financas', true, NULL),
  ('c6 bank', 'C6 Bank', 'c6bank.com.br', 'financas', true, NULL),
  ('picpay', 'Picpay', 'picpay.com', 'financas', true, NULL),
  ('pagseguro', 'Pagseguro', 'pagseguro.com', 'financas', true, NULL),
  ('paypal', 'Paypal', 'paypal.com', 'financas', true, NULL),
  ('stone', 'Stone', 'stone.com.br', 'financas', true, NULL),
  ('cielo', 'Cielo', 'cielo.com.br', 'financas', true, NULL),
  ('rede', 'Rede', 'userede.com.br', 'financas', true, NULL),
  ('getnet', 'Getnet', 'getnet.com.br', 'financas', true, NULL),
  ('sicredi', 'Sicredi', 'sicredi.com.br', 'financas', true, NULL),
  ('sicoob', 'Sicoob', 'sicoob.com.br', 'financas', true, NULL),
  ('cresol', 'Cresol', 'cresol.com.br', 'financas', true, NULL),
  ('unicred', 'Unicred', 'unicred.com.br', 'financas', true, NULL),
  ('neon', 'Neon', 'neon.com.br', 'financas', true, NULL),
  ('next', 'Next', 'next.me', 'financas', true, NULL),
  ('agibank', 'Agibank', 'agibank.com.br', 'financas', true, NULL),
  ('pan', 'Pan', 'bancopan.com.br', 'financas', true, NULL),
  ('bmg', 'Bmg', 'bancobmg.com.br', 'financas', true, NULL),
  ('daycoval', 'Daycoval', 'daycoval.com.br', 'financas', true, NULL),
  ('safra', 'Safra', 'safra.com.br', 'financas', true, NULL),
  ('bv', 'Bv', 'bancobv.com.br', 'financas', true, NULL),
  ('original', 'Original', 'bancoriginal.com.br', 'financas', true, NULL),
  ('modal', 'Modal', 'bancomodal.com.br', 'financas', true, NULL),
  ('banrisul', 'Banrisul', 'banrisul.com.br', 'financas', true, NULL),
  ('brb', 'Brb', 'brb.com.br', 'financas', true, NULL),
  ('banese', 'Banese', 'banese.com.br', 'financas', true, NULL),
  ('mercantil', 'Mercantil', 'mercantil.com.br', 'financas', true, NULL),
  ('creditas', 'Creditas', 'creditas.com', 'financas', true, NULL),
  ('serasa', 'Serasa', 'serasa.com.br', 'financas', true, NULL),
  ('wise', 'Wise', 'wise.com', 'financas', true, NULL),
  ('remessa online', 'Remessa Online', 'remessaonline.com.br', 'financas', true, NULL),
  ('nomad', 'Nomad', 'nomadglobal.com', 'financas', true, NULL),
  ('avenue', 'Avenue', 'avenue.us', 'financas', true, NULL),
  ('rico', 'Rico', 'rico.com.vc', 'financas', true, NULL),
  ('clear', 'Clear', 'clear.com.br', 'financas', true, NULL),
  ('nuinvest', 'Nuinvest', 'nuinvest.com.br', 'financas', true, NULL),
  ('warren', 'Warren', 'warren.com.br', 'financas', true, NULL),
  ('toro investimentos', 'Toro Investimentos', 'toroinvestimentos.com.br', 'financas', true, NULL),
  ('genial', 'Genial', 'genialinvestimentos.com.br', 'financas', true, NULL),
  ('orama', 'Orama', 'orama.com.br', 'financas', true, NULL),
  ('guide', 'Guide', 'guideinvestimentos.com.br', 'financas', true, NULL),
  ('agora', 'Agora', 'agorainvestimentos.com.br', 'financas', true, NULL),
  ('american express', 'American Express', 'americanexpress.com', 'financas', true, NULL),
  ('western union', 'Western Union', 'westernunion.com', 'financas', true, NULL),
  -- viagens
  ('latam', 'Latam', 'latam.com', 'viagens', true, NULL),
  ('gol', 'Gol', 'voegol.com.br', 'viagens', true, NULL),
  ('azul', 'Azul', 'voeazul.com.br', 'viagens', true, NULL),
  ('tap', 'Tap', 'flytap.com', 'viagens', true, NULL),
  ('american airlines', 'American Airlines', 'aa.com', 'viagens', true, NULL),
  ('united', 'United', 'united.com', 'viagens', true, NULL),
  ('delta', 'Delta', 'delta.com', 'viagens', true, NULL),
  ('air france', 'Air France', 'airfrance.com', 'viagens', true, NULL),
  ('klm', 'Klm', 'klm.com', 'viagens', true, NULL),
  ('iberia', 'Iberia', 'iberia.com', 'viagens', true, NULL),
  ('emirates', 'Emirates', 'emirates.com', 'viagens', true, NULL),
  ('turkish airlines', 'Turkish Airlines', 'turkishairlines.com', 'viagens', true, NULL),
  ('copa airlines', 'Copa Airlines', 'copaair.com', 'viagens', true, NULL),
  ('airbnb', 'Airbnb', 'airbnb.com', 'viagens', true, NULL),
  ('booking', 'Booking', 'booking.com', 'viagens', true, NULL),
  ('expedia', 'Expedia', 'expedia.com.br', 'viagens', true, NULL),
  ('decolar', 'Decolar', 'decolar.com', 'viagens', true, NULL),
  ('despegar', 'Despegar', 'despegar.com', 'viagens', true, NULL),
  ('trivago', 'Trivago', 'trivago.com.br', 'viagens', true, NULL),
  ('hotels.com', 'Hotels.com', 'hotels.com', 'viagens', true, NULL),
  ('cvc', 'Cvc', 'cvc.com.br', 'viagens', true, NULL),
  ('viajanet', 'Viajanet', 'viajanet.com.br', 'viagens', true, NULL),
  ('maxmilhas', 'Maxmilhas', 'maxmilhas.com.br', 'viagens', true, NULL),
  ('smiles', 'Smiles', 'smiles.com.br', 'viagens', true, NULL),
  ('latam pass', 'Latam Pass', 'latampass.com', 'viagens', true, NULL),
  ('tudoazul', 'Tudoazul', 'tudoazul.com', 'viagens', true, NULL),
  ('accor', 'Accor', 'accor.com', 'viagens', true, NULL),
  ('ibis', 'Ibis', 'ibis.com', 'viagens', true, NULL),
  ('novotel', 'Novotel', 'novotel.com', 'viagens', true, NULL),
  ('hilton', 'Hilton', 'hilton.com', 'viagens', true, NULL),
  ('marriott', 'Marriott', 'marriott.com', 'viagens', true, NULL),
  ('hyatt', 'Hyatt', 'hyatt.com', 'viagens', true, NULL),
  ('best western', 'Best Western', 'bestwestern.com', 'viagens', true, NULL),
  ('intercontinental', 'Intercontinental', 'ihg.com', 'viagens', true, NULL),
  ('holiday inn', 'Holiday Inn', 'ihg.com', 'viagens', true, NULL),
  ('radisson', 'Radisson', 'radisson.com', 'viagens', true, NULL),
  ('golden tulip', 'Golden Tulip', 'goldentulip.com', 'viagens', true, NULL),
  ('windsor', 'Windsor', 'windsorhoteis.com.br', 'viagens', true, NULL),
  ('blue tree', 'Blue Tree', 'bluetree.com.br', 'viagens', true, NULL),
  ('club med', 'Club Med', 'clubmed.com.br', 'viagens', true, NULL),
  -- games
  ('steam', 'Steam', 'store.steampowered.com', 'games', true, NULL),
  ('playstation', 'Playstation', 'playstation.com', 'games', true, NULL),
  ('xbox', 'Xbox', 'xbox.com', 'games', true, NULL),
  ('nintendo', 'Nintendo', 'nintendo.com.br', 'games', true, NULL),
  ('epic games', 'Epic Games', 'epicgames.com', 'games', true, NULL),
  ('riot games', 'Riot Games', 'riotgames.com', 'games', true, NULL),
  ('blizzard', 'Blizzard', 'blizzard.com', 'games', true, NULL),
  ('ea games', 'Ea Games', 'ea.com', 'games', true, NULL),
  ('ubisoft', 'Ubisoft', 'ubisoft.com', 'games', true, NULL),
  ('rockstar games', 'Rockstar Games', 'rockstargames.com', 'games', true, NULL),
  ('twitch', 'Twitch', 'twitch.tv', 'games', true, NULL),
  ('discord', 'Discord', 'discord.com', 'games', true, NULL),
  -- tecnologia
  ('google play', 'Google Play', 'play.google.com', 'tecnologia', true, NULL),
  ('apple', 'Apple', 'apple.com', 'tecnologia', true, NULL),
  ('icloud', 'Icloud', 'apple.com', 'tecnologia', true, NULL),
  ('microsoft', 'Microsoft', 'microsoft.com', 'tecnologia', true, NULL),
  ('google one', 'Google One', 'one.google.com', 'tecnologia', true, NULL),
  ('adobe', 'Adobe', 'adobe.com', 'tecnologia', true, NULL),
  ('canva', 'Canva', 'canva.com', 'tecnologia', true, NULL),
  ('figma', 'Figma', 'figma.com', 'tecnologia', true, NULL),
  ('dropbox', 'Dropbox', 'dropbox.com', 'tecnologia', true, NULL),
  ('github', 'Github', 'github.com', 'tecnologia', true, NULL),
  ('openai', 'Openai', 'openai.com', 'tecnologia', true, NULL),
  ('chatgpt', 'Chatgpt', 'openai.com', 'tecnologia', true, NULL),
  ('samsung', 'Samsung', 'samsung.com.br', 'tecnologia', true, NULL),
  ('dell', 'Dell', 'dell.com', 'tecnologia', true, NULL),
  ('lenovo', 'Lenovo', 'lenovo.com', 'tecnologia', true, NULL),
  ('hp', 'Hp', 'hp.com', 'tecnologia', true, NULL),
  ('positivo tecnologia', 'Positivo Tecnologia', 'positivotecnologia.com.br', 'tecnologia', true, NULL),
  ('motorola', 'Motorola', 'motorola.com.br', 'tecnologia', true, NULL),
  ('xiaomi', 'Xiaomi', 'mi.com', 'tecnologia', true, NULL),
  ('logitech', 'Logitech', 'logitech.com', 'tecnologia', true, NULL),
  ('razer', 'Razer', 'razer.com', 'tecnologia', true, NULL),
  ('nordvpn', 'Nordvpn', 'nordvpn.com', 'tecnologia', true, NULL),
  ('expressvpn', 'Expressvpn', 'expressvpn.com', 'tecnologia', true, NULL),
  ('zoom', 'Zoom', 'zoom.us', 'tecnologia', true, NULL),
  ('notion', 'Notion', 'notion.so', 'tecnologia', true, NULL),
  ('slack', 'Slack', 'slack.com', 'tecnologia', true, NULL),
  ('aws', 'Aws', 'aws.amazon.com', 'tecnologia', true, NULL),
  ('digitalocean', 'Digitalocean', 'digitalocean.com', 'tecnologia', true, NULL),
  ('cloudflare', 'Cloudflare', 'cloudflare.com', 'tecnologia', true, NULL),
  -- casa
  ('leroy merlin', 'Leroy Merlin', 'leroymerlin.com.br', 'casa', true, NULL),
  ('tokstok', 'Tokstok', 'tokstok.com.br', 'casa', true, NULL),
  ('ikea', 'Ikea', 'ikea.com', 'casa', true, NULL),
  ('madeiramadeira', 'Madeiramadeira', 'madeiramadeira.com.br', 'casa', true, NULL),
  ('camicado', 'Camicado', 'camicado.com.br', 'casa', true, NULL),
  ('westwing', 'Westwing', 'westwing.com.br', 'casa', true, NULL),
  ('mobly', 'Mobly', 'mobly.com.br', 'casa', true, NULL),
  ('telhanorte', 'Telhanorte', 'telhanorte.com.br', 'casa', true, NULL),
  ('quero-quero', 'Quero-quero', 'queroquero.com.br', 'casa', true, NULL),
  ('havan', 'Havan', 'havan.com.br', 'casa', true, NULL),
  ('colombo', 'Colombo', 'colombo.com.br', 'casa', true, NULL),
  ('tramontina', 'Tramontina', 'tramontina.com.br', 'casa', true, NULL),
  ('ri happy', 'Ri Happy', 'rihappy.com.br', 'casa', true, NULL),
  ('pbkids', 'Pbkids', 'pbkids.com.br', 'casa', true, NULL),
  ('lego', 'Lego', 'lego.com', 'casa', true, NULL),
  ('brastemp', 'Brastemp', 'brastemp.com.br', 'casa', true, NULL),
  ('consul', 'Consul', 'consul.com.br', 'casa', true, NULL),
  ('electrolux', 'Electrolux', 'electrolux.com.br', 'casa', true, NULL),
  ('bosch', 'Bosch', 'bosch.com.br', 'casa', true, NULL),
  ('philips', 'Philips', 'philips.com.br', 'casa', true, NULL),
  ('multilaser', 'Multilaser', 'multilaser.com.br', 'casa', true, NULL),
  ('mondial', 'Mondial', 'mondial.com.br', 'casa', true, NULL),
  ('britania', 'Britania', 'britania.com.br', 'casa', true, NULL),
  ('arno', 'Arno', 'arno.com.br', 'casa', true, NULL),
  ('oster', 'Oster', 'osterbrasil.com.br', 'casa', true, NULL),
  ('tok stok', 'Tok Stok', 'tokstok.com.br', 'casa', true, NULL),
  ('casa e video', 'Casa E Video', 'casaevideo.com.br', 'casa', true, NULL),
  ('etna', 'Etna', 'etna.com.br', 'casa', true, NULL),
  ('cassol', 'Cassol', 'cassol.com.br', 'casa', true, NULL),
  ('c&c', 'C&c', 'cec.com.br', 'casa', true, NULL),
  -- pet
  ('petlove', 'Petlove', 'petlove.com.br', 'pet', true, NULL),
  ('petz', 'Petz', 'petz.com.br', 'pet', true, NULL),
  ('cobasi', 'Cobasi', 'cobasi.com.br', 'pet', true, NULL),
  ('zee dog', 'Zee Dog', 'zee.dog', 'pet', true, NULL),
  ('pet center', 'Pet Center', 'petcenter.com.br', 'pet', true, NULL),
  ('doghero', 'Doghero', 'doghero.com.br', 'pet', true, NULL),
  ('pet anjo', 'Pet Anjo', 'petanjo.com', 'pet', true, NULL),
  ('dr. pet', 'Dr. Pet', 'drpet.com.br', 'pet', true, NULL),
  ('agrocat', 'Agrocat', 'agrocat.com.br', 'pet', true, NULL),
  ('purina', 'Purina', 'purina.com.br', 'pet', true, NULL),
  ('royal canin', 'Royal Canin', 'royalcanin.com', 'pet', true, NULL),
  ('hills', 'Hills', 'hillspet.com', 'pet', true, NULL),
  ('golden', 'Golden', 'goldenalimentos.com.br', 'pet', true, NULL),
  ('premier pet', 'Premier Pet', 'premierpet.com.br', 'pet', true, NULL),
  ('guabi', 'Guabi', 'guabi.com.br', 'pet', true, NULL),
  ('biofresh', 'Biofresh', 'biofresh.com.br', 'pet', true, NULL),
  ('racao total', 'Racao Total', 'racaototal.com.br', 'pet', true, NULL),
  ('granplus', 'Granplus', 'granplus.com.br', 'pet', true, NULL),
  ('tudo de bicho', 'Tudo De Bicho', 'tudodebicho.com.br', 'pet', true, NULL),
  ('agropet', 'Agropet', 'agropet.com.br', 'pet', true, NULL),
  ('mundo animal', 'Mundo Animal', 'mundoanimal.com.br', 'pet', true, NULL),
  ('vetshop', 'Vetshop', 'vetshop.com.br', 'pet', true, NULL),
  ('aminopet', 'Aminopet', 'aminopet.com.br', 'pet', true, NULL),
  ('petland', 'Petland', 'petland.com.br', 'pet', true, NULL),
  -- beleza
  ('o boticario', 'O Boticario', 'boticario.com.br', 'beleza', true, NULL),
  ('natura', 'Natura', 'natura.com.br', 'beleza', true, NULL),
  ('avon', 'Avon', 'avon.com.br', 'beleza', true, NULL),
  ('sephora', 'Sephora', 'sephora.com.br', 'beleza', true, NULL),
  ('loreal', 'Loreal', 'loreal.com.br', 'beleza', true, NULL),
  ('eudora', 'Eudora', 'eudora.com.br', 'beleza', true, NULL),
  ('vult', 'Vult', 'vult.com.br', 'beleza', true, NULL),
  ('quem disse berenice', 'Quem Disse Berenice', 'quemdisseberenice.com.br', 'beleza', true, NULL),
  ('ruby rose', 'Ruby Rose', 'rubyrose.com.br', 'beleza', true, NULL),
  ('mac cosmetics', 'Mac Cosmetics', 'maccosmetics.com.br', 'beleza', true, NULL),
  ('maybelline', 'Maybelline', 'maybelline.com.br', 'beleza', true, NULL),
  ('nivea', 'Nivea', 'nivea.com.br', 'beleza', true, NULL),
  ('neutrogena', 'Neutrogena', 'neutrogena.com.br', 'beleza', true, NULL),
  ('la roche-posay', 'La Roche-posay', 'laroche-posay.com.br', 'beleza', true, NULL),
  ('cerave', 'Cerave', 'cerave.com.br', 'beleza', true, NULL),
  ('vichy', 'Vichy', 'vichy.com.br', 'beleza', true, NULL),
  ('wella', 'Wella', 'wella.com', 'beleza', true, NULL),
  ('schwarzkopf', 'Schwarzkopf', 'schwarzkopf.com.br', 'beleza', true, NULL),
  ('garnier', 'Garnier', 'garnier.com.br', 'beleza', true, NULL),
  ('salon line', 'Salon Line', 'salonline.com.br', 'beleza', true, NULL),
  ('ikesaki', 'Ikesaki', 'ikesaki.com.br', 'beleza', true, NULL),
  ('studio 35', 'Studio 35', 'studio35.com.br', 'beleza', true, NULL),
  ('jequiti', 'Jequiti', 'jequiti.com.br', 'beleza', true, NULL),
  ('mary kay', 'Mary Kay', 'marykay.com.br', 'beleza', true, NULL),
  ('hinode', 'Hinode', 'hinode.com.br', 'beleza', true, NULL),
  ('amway', 'Amway', 'amway.com.br', 'beleza', true, NULL),
  ('gillette', 'Gillette', 'gillette.com.br', 'beleza', true, NULL),
  ('dove', 'Dove', 'dove.com', 'beleza', true, NULL),
  ('nars', 'Nars', 'narscosmetics.com', 'beleza', true, NULL),
  ('urban decay', 'Urban Decay', 'urbandecay.com', 'beleza', true, NULL),
  ('anastasia beverly hills', 'Anastasia Beverly Hills', 'anastasiabeverlyhills.com', 'beleza', true, NULL),
  ('the ordinary', 'The Ordinary', 'theordinary.com', 'beleza', true, NULL),
  ('beauty color', 'Beauty Color', 'beautycolor.com.br', 'beleza', true, NULL)
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
    description_override TEXT,
    tags_override_json   TEXT,
    match_count   INT         NOT NULL DEFAULT 0,
    deleted_at    TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_transaction_rules_has_target
        CHECK (
            category_id          IS NOT NULL OR
            merchant_id          IS NOT NULL OR
            description_override IS NOT NULL OR
            tags_override_json   IS NOT NULL
        )
);

CREATE INDEX idx_category_rules_account_id
    ON banking.transaction_rules(account_id)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_category_rules_system
    ON banking.transaction_rules(is_system)
    WHERE is_system = true AND deleted_at IS NULL;

-- V0_29__seed_category_rules.sql
-- Comprehensive system auto-categorization rules derived from the historical
-- merchant seed used before this migration.
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
SELECT NULL, cat.id, p.pattern, 'CONTAINS', 'ANY', 100, true
FROM (SELECT id FROM banking.transaction_categories WHERE name = 'PIX' AND applicable_to = 'ANY' AND is_system = true LIMIT 1) AS cat
CROSS JOIN (VALUES ('pix')) AS p(pattern);

-- Final pass: link seeded rules to merchants after the full rule seed is in place.
-- A subset of rule patterns are intentionally aliases of the merchant name that
-- appears in normalized card descriptions (e.g. 99app -> 99, ray ban -> ray-ban).
WITH pattern_aliases(rule_pattern, merchant_name) AS (
    VALUES
        ('99app', '99'),
        ('araujo farma', 'araujo'),
        ('bp beneficencia', 'bp beneficencia portuguesa'),
        ('budget car', 'budget'),
        ('desktop fibra', 'desktop'),
        ('disney', 'disney+'),
        ('dr consulta', 'dr. consulta'),
        ('equatorial energia', 'equatorial'),
        ('grow bike', 'grow'),
        ('hospital oswaldo cruz', 'oswaldo cruz'),
        ('indigo park', 'indigo'),
        ('levis', 'levi''s'),
        ('paramount', 'paramount+'),
        ('patroni', 'patroni pizza'),
        ('ray ban', 'ray-ban'),
        ('rede dor', 'rede d''or'),
        ('restaurante spot', 'spot'),
        ('sky brasil', 'sky'),
        ('unidas rent', 'unidas'),
        ('urbs curitiba', 'urbs')
),
matched_rules AS (
    SELECT
        tr.id AS rule_id,
        COALESCE(
            (
                SELECT m.id
                FROM banking.merchants m
                WHERE m.account_id = tr.account_id
                  AND m.merchant_name = tr.pattern
                ORDER BY m.created_at ASC
                LIMIT 1
            ),
            (
                SELECT m.id
                FROM banking.merchants m
                WHERE m.account_id IS NULL
                  AND m.is_system = true
                  AND m.merchant_name = tr.pattern
                ORDER BY m.created_at ASC
                LIMIT 1
            ),
            (
                SELECT m.id
                FROM pattern_aliases pa
                JOIN banking.merchants m
                    ON m.merchant_name = pa.merchant_name
                WHERE pa.rule_pattern = tr.pattern
                  AND m.account_id IS NULL
                  AND m.is_system = true
                ORDER BY m.created_at ASC
                LIMIT 1
            )
        ) AS merchant_id
    FROM banking.transaction_rules tr
    WHERE tr.deleted_at IS NULL
      AND tr.merchant_id IS NULL
)
UPDATE banking.transaction_rules tr
SET merchant_id = mr.merchant_id,
    updated_at = NOW()
FROM matched_rules mr
WHERE tr.id = mr.rule_id
  AND mr.merchant_id IS NOT NULL;
