import { Metadata } from 'next';
import NationalIndicatorsClient from './NationalIndicatorsClient';

const INDICATORS_INFO = {
  "IPCA": { 
    "label": "IPCA", 
    "description": "Índice Nacional de Preços ao Consumidor Amplo. É a inflação oficial do Brasil e a principal referência para a meta de inflação.", 
    "issuer": "IBGE" 
  }, 
  "IPCA_15": { 
    "label": "IPCA-15", 
    "description": "Prévia do IPCA (Índice Nacional de Preços ao Consumidor Amplo 15). Antecipação da tendência da inflação oficial no mês.", 
    "issuer": "IBGE" 
  }, 
  "INPC": { 
    "label": "INPC", 
    "description": "Índice Nacional de Preços ao Consumidor. Mede a inflação para famílias de menor renda e é usado em reajustes salariais.", 
    "issuer": "IBGE" 
  }, 
  "IGP_M": { 
    "label": "IGP-M", 
    "description": "Índice Geral de Preços do Mercado. Mede variação de preços na economia (atacado, consumidor e construção) e é comum em contratos de aluguel.", 
    "issuer": "IPEA/FGV" 
  }, 
  "IGP_DI": { 
    "label": "IGP-DI", 
    "description": "Índice Geral de Preços – Disponibilidade Interna. Acompanha a inflação em diferentes estágios da economia para análise macroeconômica.", 
    "issuer": "IPEA/FGV" 
  }, 
  "SELIC": { 
    "label": "Meta Selic", 
    "description": "Sistema Especial de Liquidação e de Custódia. É a taxa básica de juros da economia, usada para controlar inflação e orientar o custo do crédito.", 
    "issuer": "BCB" 
  }, 
  "CDI": { 
    "label": "CDI", 
    "description": "Certificado de Depósito Interbancário. Taxa de referência das operações entre bancos de curtíssimo prazo, base de muitos investimentos.", 
    "issuer": "B3" 
  }, 
  "DI_OVER": { 
    "label": "DI Over", 
    "description": "Taxa média diária dos Depósitos Interfinanceiros (overnight). Indica o custo do dinheiro entre bancos no curtíssimo prazo.", 
    "issuer": "B3" 
  }, 
  "TR": { 
    "label": "Taxa Referencial", 
    "description": "Utilizado principalmente como taxa de correção monetária. Ela serve para atualizar o saldo da poupança, FGTS, títulos de capitalização e calcular o custo de certos financiamentos imobiliários (sistema SFH).", 
    "issuer": "BCB" 
  }, 
  "GDP": { 
    "label": "PIB", 
    "description": "Produto Interno Bruto. Soma de bens e serviços finais produzidos no país, principal medida de atividade econômica.", 
    "issuer": "IBGE" 
  }, 
  "UNEMPLOYMENT": { 
    "label": "Taxa de Desemprego", 
    "description": "Percentual da força de trabalho que está sem emprego e procurando trabalho. Mede o grau de ociosidade do mercado de trabalho.", 
    "issuer": "IBGE" 
  }, 
  "AVERAGE_INCOME": { 
    "label": "Renda Média Real", 
    "description": "Rendimento médio do trabalho ajustado pela inflação. Mostra o poder de compra da população ocupada.", 
    "issuer": "IBGE" 
  }, 
  "TRADE_BALANCE": { 
    "label": "Balança Comercial", 
    "description": "Diferença entre exportações e importações de bens. Indica se o país vende ao exterior mais do que compra.", 
    "issuer": "SECEX" 
  }, 
  "CURRENT_ACCOUNT": { 
    "label": "Conta Corrente", 
    "description": "Saldo de transações com o exterior em bens, serviços, rendas e transferências. Mostra necessidade ou capacidade de financiamento externo.", 
    "issuer": "BCB" 
  }, 
  "INTERNATIONAL_RESERVES": { 
    "label": "Reservas Internacionais", 
    "description": "Ativos em moeda estrangeira mantidos pelo Banco Central. Servem para reduzir vulnerabilidade externa e dar estabilidade cambial.", 
    "issuer": "BCB" 
  }, 
  "PUBLIC_DEBT_GDP": { 
    "label": "Dívida Pública (% do PIB)", 
    "description": "Endividamento do governo geral em relação ao PIB. A dívida bruta mede o total de obrigações; a líquida desconta os ativos financeiros.", 
    "issuer": "BCB" 
  }, 
  "PRIMARY_RESULT": { 
    "label": "Resultado Primário", 
    "description": "Diferença entre receitas e despesas do governo, sem considerar juros da dívida. Indica esforço fiscal corrente.", 
    "issuer": "BCB" 
  } 
};

export const metadata: Metadata = {
  title: "Indicadores Econômicos Nacionais (Brasil) - IPCA, Selic, PIB | Invistus",
  description: "Acompanhe os principais indicadores econômicos do Brasil em tempo real: IPCA, Selic, PIB, Taxa de Desemprego, Balança Comercial e mais. Dados oficiais do IBGE, BCB e FGV.",
  keywords: [
    "indicadores econômicos", "economia brasil", "IPCA", "Selic", "PIB", 
    "inflação", "taxa de juros", "desemprego", "renda média", 
    "balança comercial", "reservas internacionais", "dívida pública"
  ],
  openGraph: {
    title: "Indicadores Econômicos Nacionais (Brasil) | Invistus",
    description: "Monitore a saúde da economia brasileira com dados atualizados de inflação (IPCA), juros (Selic), crescimento (PIB) e emprego.",
    type: "website",
    locale: "pt_BR",
  }
};

export default function NationalIndicatorsPage() {
  const jsonLd = {
    "@context": "https://schema.org",
    "@type": "DefinedTermSet",
    "name": "Indicadores Econômicos Nacionais",
    "description": "Lista dos principais indicadores macroeconômicos do Brasil monitorados pelo Invistus.",
    "definedTerm": Object.values(INDICATORS_INFO).map((ind) => ({
      "@type": "DefinedTerm",
      "name": ind.label,
      "description": ind.description,
      "termCode": ind.label,
      "inDefinedTermSet": {
         "@type": "DefinedTermSet",
         "name": "Invistus National Indicators",
         "url": "https://invistus.com.br/financial-data/national-indicators"
      }
    }))
  };

  return (
    <>
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: JSON.stringify(jsonLd) }}
      />
      <NationalIndicatorsClient />
    </>
  );
}
