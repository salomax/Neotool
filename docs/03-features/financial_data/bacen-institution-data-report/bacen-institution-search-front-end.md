# Module Financial Data  

There'll be a new module called `financialdata`

# Feature Bacen Institution Data Report

This new feature will allow the user to:
- Search by a name or CNPJ for a Bacen Institution
- Open the Bacen Institution Data Reports for analysis

# Prototypes

- design/prototypes/features/bacen-institution-report-data/bacen-institution-report-data-prototype.png

- design/prototypes/features/bacen-institution-report-data/bacen-institution-report-data-prototype.html

# Page 1 - Search Bacen Institution by Name or CNPJ

Page title: 
- Financial Institutions (en)
- Instituições Financeiras (pt-BR)

There'll be available
- A new icon on left menu with some Bank or Bank Data as icon
- Labels en: Bacen Bank Report or in pt-BR: Relatórios Bancários do Bacen
- The main container page will split into 2 parts
- On the top fixed, an Autocomplete Search (SearchableAutocomplete.tsx), based on what the user types, aligned to the center, and fixed
- Below, we have a new component, a Card Grid with infinite Scroll


# New UI Components

## Card Grid with infinite Scroll

- If the user scroll down until certain point of the scroll, a new request must be done (or Find the best and common approach as most of the AAA components do for infinite Scroll)
- Number of cards per row must be parameterized

## Card (Generic)

## Bacen Institution Card (Using Card as wrapper)

See this mock up (just a inspiration):

<div class="flex items-center gap-4 mb-6">
<div class="w-14 h-14 rounded-full border border-gray-100 bg-white p-1 shadow-sm shrink-0">
<img alt="Logo Banco F" class="w-full h-full object-contain rounded-full" src="..."/>
</div>
<div>
<h3 class="font-bold text-lg text-foreground leading-tight group-hover:text-primary transition-colors">Banco F</h3>
<p class="text-xs text-muted-foreground mt-1 font-medium">Banco Múltiplo</p>
</div>
</div>
<div class="grid grid-cols-2 gap-y-5 gap-x-2">
<div class="space-y-1">
<p class="text-[10px] uppercase tracking-wider font-semibold text-muted-foreground">Lucro Líquido</p>
<p class="text-sm font-bold text-foreground">R$ 30 bi</p>
</div>
<div class="space-y-1">
<p class="text-[10px] uppercase tracking-wider font-semibold text-muted-foreground">Patrimônio</p>
<p class="text-sm font-bold text-foreground">R$ 150 bi</p>
</div>
<div class="space-y-1">
<p class="text-[10px] uppercase tracking-wider font-semibold text-muted-foreground">Cresc. YoY</p>
<div class="flex items-center text-emerald-600 font-bold text-sm">
<span class="material-symbols-outlined text-base mr-0.5">trending_up</span>
                                            +12%
                                        </div>
</div>
<div class="space-y-1">
<p class="text-[10px] uppercase tracking-wider font-semibold text-muted-foreground">Ind. Basileia</p>
<div class="flex items-center gap-1.5">
<span class="w-2 h-2 rounded-full bg-emerald-500 shadow-[0_0_8px_rgba(16,185,129,0.4)]"></span>
<span class="text-sm font-bold text-foreground">17.5%</span>
</div>
</div>
</div>
</div>
<div class="bg-slate-100 px-6 py-3 border-t border-slate-200 flex items-center justify-between">
<span class="text-[11px] font-bold text-slate-500 uppercase tracking-wider">Instituição Individual</span>
<span class="material-symbols-outlined text-slate-400 text-lg">domain</span>
</div>


# Backend

## Searchable Autocomplete Query

```graphql
query SearchBacenInstitutionsAutocomplete($query: String!) {
  searchBacenInstitutionsAutocomplete(query: $query) {
    suggestions {
      id
      name
      cnpj
      logoUrl
    }
  }
}
```
## Bacen Intitution Query

```graphql
query BacenInstitutions($query: String, $first: Int) {
  bacenInstitutions(query: $query, first: $first) {
    edges {
      node {
        id
        name
        logoUrl
        profit
        equity
        yoyEquityGrowth
        basileiaIndex
        institutionType
      }
      cursor
    }
    pageInfo {
      hasNextPage
      hasPreviousPage
      startCursor
      endCursor
    }
    totalCount
  }
}
```

first 50!


