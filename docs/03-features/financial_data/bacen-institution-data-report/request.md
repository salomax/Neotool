



# Query


BacenFinanceInstitution
- currentYearMonth: bacen_bank_report.ano_mes (latest ano-mes)
- asset: financial_institution.asset.id
- tradeName: financial_institution.trade_name
- legalName: financial_institution.legal_name
- website: financial_institution.website
- socialNetworks: [financial_institution.social_network]
- mainActivity: financial_institution.receita_data.mainActivity.text
- address: financial_institution.receita_data.address.city; financial_institution.receita_data.address.state
- founded: financial_institution.receita_data.founded (format to locale date)
- controlType: bacen_institution.tipo_controle: (1) Público (2) Privado nacional (3) Privado com controle estrangeiro
- institutionType: bacen_institution.tipo_consolidacao: (I) Instituição independente/individual Institution ou (C) Conglomerado/Group
- institutionBankType: bacen_institution.tipo_consolidado_bancario: Tipo de consolidado bancário: (b1)Banco comercial, Banco múltiplo com carteira comercial ou caixas econômicas (B2) Banco múltiplo sem carteira comercial ou banco de câmbio ou banco de investimento (B3S) cooperativa de crédito singular (B3C) Central e confederação de cooperativas de crédito (b4) banco de desenvolvimento (N1) Não bancário de crédito (n2) Não bancário do mercado de capitais (N4) Instituições de pagamento. 
- basileiaIndex
- mobilizacaoIndex
- profit: Lucro Líquido (R$)
- equity: Patrimônio Líquido (R$)
- totalAssets: Ativo Total (R$)
- funding: Captações (R$)
- loanPortfolio: Carteira de Crédito Classificada (R$)
- RWAReferenceEquity: Patrimônio de Referência RWA (R$) 
- reports: bacen_bank_report.numero_relatorio
    - name: Nome do relatório: bacen_bank_report_catalog.nome_relatorio
        - metrics:
            - group: bacen_bank_report_account.grupo
            - metric_id: bacen_bank_report_account.conta
            - name: bacen_bank_report_account.nome_coluna
            - yearMonths: (x -> number of yearMonths from now)
                - yearMonth: bacen_bank_report.ano_mes
                - value: bacen_bank_report_data.saldo



{
  "head": true,
  "alias": null,
  "taxId": "59588111000103",
  "emails": [
    {
      "domain": "bv.com.br",
      "address": "intimacoesoficiais@bv.com.br",
      "ownership": "CORPORATE"
    }
  ],
  "phones": [
    {
      "area": "11",
      "type": "LANDLINE",
      "number": "51712488"
    },
    {
      "area": "11",
      "type": "LANDLINE",
      "number": "51712672"
    }
  ],
  "status": {
    "id": 2,
    "text": "Ativa"
  },
  "address": {
    "zip": "04794000",
    "city": "São Paulo",
    "state": "SP",
    "number": "14171",
    "street": "Avenida das Nacoes Unidas",
    "country": {
      "id": 76,
      "name": "Brasil"
    },
    "details": "Torre a Andar 18",
    "district": "Vila Gertrudes",
    "municipality": 3550308
  },
  "company": {
    "id": 59588111,
    "name": "BANCO VOTORANTIM S.A.",
    "size": {
      "id": 5,
      "text": "Demais",
      "acronym": "DEMAIS"
    },
    "simei": {
      "since": null,
      "optant": false
    },
    "equity": 8480372195.25,
    "nature": {
      "id": 2054,
      "text": "Sociedade Anônima Fechada"
    },
    "members": [
      {
        "role": {
          "id": 8,
          "text": "Conselheiro de Administração"
        },
        "since": "2023-11-30",
        "person": {
          "id": "073d59d3-1fdd-4848-9b95-363e9c6034e9",
          "age": "61-70",
          "name": "Odilon Pinheiro de Almeida Junior",
          "type": "NATURAL",
          "taxId": "***645888**"
        }
      },
      {
        "role": {
          "id": 10,
          "text": "Diretor"
        },
        "since": "2022-11-11",
        "person": {
          "id": "0ed3b273-7845-4dae-b727-8ebdfa924134",
          "age": "51-60",
          "name": "Carlos Renato Bonetti",
          "type": "NATURAL",
          "taxId": "***198248**"
        }
      },
      {
        "role": {
          "id": 16,
          "text": "Presidente"
        },
        "since": "2025-04-28",
        "person": {
          "id": "153378c2-fb43-4072-85da-aa4cbfdcc867",
          "age": "41-50",
          "name": "Gustavo Henrique Santos de Sousa",
          "type": "NATURAL",
          "taxId": "***831394**"
        }
      },
      {
        "role": {
          "id": 8,
          "text": "Conselheiro de Administração"
        },
        "since": "2023-08-17",
        "person": {
          "id": "20b2b02a-6e74-4e04-9190-0eb878b35388",
          "age": "41-50",
          "name": "Francisco Augusto Lassalvia",
          "type": "NATURAL",
          "taxId": "***355918**"
        }
      },
      {
        "role": {
          "id": 8,
          "text": "Conselheiro de Administração"
        },
        "since": "2023-07-28",
        "person": {
          "id": "280be802-22f5-4ce9-b09d-f5160f2695e1",
          "age": "41-50",
          "name": "Felipe Guimaraes Geissler Prince",
          "type": "NATURAL",
          "taxId": "***345856**"
        }
      },
      {
        "role": {
          "id": 8,
          "text": "Conselheiro de Administração"
        },
        "since": "2016-08-15",
        "person": {
          "id": "326ca0dd-d6fc-4b72-882a-fb5d408c8d4c",
          "age": "51-60",
          "name": "Paulo Rogerio Caffarelli",
          "type": "NATURAL",
          "taxId": "***887279**"
        }
      },
      {
        "role": {
          "id": 10,
          "text": "Diretor"
        },
        "since": "2020-11-30",
        "person": {
          "id": "3af081e4-d602-49a7-af71-d41e0f78216f",
          "age": "41-50",
          "name": "Ronaldo Medrado Helpe",
          "type": "NATURAL",
          "taxId": "***642048**"
        }
      },
      {
        "role": {
          "id": 10,
          "text": "Diretor"
        },
        "since": "2025-07-10",
        "person": {
          "id": "4877291f-1580-410b-81dd-4635bbeb48cb",
          "age": "41-50",
          "name": "Marcos Oliveira Garcia",
          "type": "NATURAL",
          "taxId": "***060127**"
        }
      },
      {
        "role": {
          "id": 10,
          "text": "Diretor"
        },
        "since": "2025-07-10",
        "person": {
          "id": "4e3a0602-19c7-412d-b629-db63c40f35de",
          "age": "41-50",
          "name": "Jamil Ganan Aley Junior",
          "type": "NATURAL",
          "taxId": "***926128**"
        }
      },
      {
        "role": {
          "id": 10,
          "text": "Diretor"
        },
        "since": "2020-06-10",
        "person": {
          "id": "54b1ebc7-46c2-4679-8d13-a59711bf1d15",
          "age": "51-60",
          "name": "Rogerio Monori",
          "type": "NATURAL",
          "taxId": "***043080**"
        }
      },
      {
        "role": {
          "id": 10,
          "text": "Diretor"
        },
        "since": "2022-03-22",
        "person": {
          "id": "5bc6388b-5f63-4acf-b44a-9afbbd13f934",
          "age": "41-50",
          "name": "Marcella Rigamonti Urada Coimbra",
          "type": "NATURAL",
          "taxId": "***497038**"
        }
      },
      {
        "role": {
          "id": 10,
          "text": "Diretor"
        },
        "since": "2022-03-22",
        "person": {
          "id": "6368f6d4-bf1c-47de-9ecf-d0a474603892",
          "age": "41-50",
          "name": "Claudia Furini Pantiga Teramoto",
          "type": "NATURAL",
          "taxId": "***543998**"
        }
      },
      {
        "role": {
          "id": 8,
          "text": "Conselheiro de Administração"
        },
        "since": "2023-07-28",
        "person": {
          "id": "63a29dc9-144b-4904-b1cb-1fb3feceda89",
          "age": "41-50",
          "name": "Tarciana Paula Gomes Medeiros",
          "type": "NATURAL",
          "taxId": "***128734**"
        }
      },
      {
        "role": {
          "id": 10,
          "text": "Diretor"
        },
        "since": "2025-07-10",
        "person": {
          "id": "69f60649-52e3-4037-9048-02bd35507770",
          "age": "41-50",
          "name": "Henrique Seije Ivamotto Passos Nogueira",
          "type": "NATURAL",
          "taxId": "***763968**"
        }
      },
      {
        "role": {
          "id": 8,
          "text": "Conselheiro de Administração"
        },
        "since": "2021-06-11",
        "person": {
          "id": "7f18f664-de23-4020-af7d-277590c4d2cf",
          "age": "41-50",
          "name": "Joao Henrique Batista de Souza Schmidt",
          "type": "NATURAL",
          "taxId": "***032489**"
        }
      },
      {
        "role": {
          "id": 8,
          "text": "Conselheiro de Administração"
        },
        "since": "2018-04-18",
        "person": {
          "id": "87f8d6f7-5194-42cd-8191-47dd31cb565e",
          "age": "51-60",
          "name": "Jairo Sampaio Saddi",
          "type": "NATURAL",
          "taxId": "***664628**"
        }
      },
      {
        "role": {
          "id": 10,
          "text": "Diretor"
        },
        "since": "2021-01-28",
        "person": {
          "id": "a6caeca7-b85d-4bb7-af51-6ba8542a7cd5",
          "age": "41-50",
          "name": "Roberto Goncalves Jabali",
          "type": "NATURAL",
          "taxId": "***408248**"
        }
      },
      {
        "role": {
          "id": 10,
          "text": "Diretor"
        },
        "since": "2019-12-05",
        "person": {
          "id": "c959028d-3e7e-43f6-8f10-402d91fd0225",
          "age": "41-50",
          "name": "Ana Paula Antunes Tarcia",
          "type": "NATURAL",
          "taxId": "***084468**"
        }
      },
      {
        "role": {
          "id": 10,
          "text": "Diretor"
        },
        "since": "2025-04-28",
        "person": {
          "id": "d00f2672-b0ee-434a-a3d8-9cddc649c50d",
          "age": "41-50",
          "name": "Marcos Emerzian Poladian",
          "type": "NATURAL",
          "taxId": "***012308**"
        }
      },
      {
        "role": {
          "id": 10,
          "text": "Diretor"
        },
        "since": "2021-08-05",
        "person": {
          "id": "d437c4c5-89bf-4f4a-9bdc-a9e66d107cb7",
          "age": "51-60",
          "name": "Alberto Wagner Teixeira Campos",
          "type": "NATURAL",
          "taxId": "***952126**"
        }
      },
      {
        "role": {
          "id": 10,
          "text": "Diretor"
        },
        "since": "2022-11-11",
        "person": {
          "id": "d4f39c82-fb0d-40d8-a20d-021fd345cf46",
          "age": "51-60",
          "name": "Walter Batlouni Junior",
          "type": "NATURAL",
          "taxId": "***231968**"
        }
      },
      {
        "role": {
          "id": 8,
          "text": "Conselheiro de Administração"
        },
        "since": "2015-06-11",
        "person": {
          "id": "d658dd56-eff3-43c5-97ae-26f566f893a9",
          "age": "61-70",
          "name": "Antonio Mauricio Maurano",
          "type": "NATURAL",
          "taxId": "***022878**"
        }
      },
      {
        "role": {
          "id": 8,
          "text": "Conselheiro de Administração"
        },
        "since": "2022-08-12",
        "person": {
          "id": "f719e1c2-c985-437a-acdc-ce8fecb38c9c",
          "age": "31-40",
          "name": "Mauro Ribeiro Neto",
          "type": "NATURAL",
          "taxId": "***002066**"
        }
      },
      {
        "role": {
          "id": 10,
          "text": "Diretor"
        },
        "since": "2021-02-17",
        "person": {
          "id": "fe120329-f63a-4f06-8de8-b02bbe1fac68",
          "age": "51-60",
          "name": "Alexandre Luiz Zimath",
          "type": "NATURAL",
          "taxId": "***727589**"
        }
      }
    ],
    "simples": {
      "since": null,
      "optant": false
    }
  },
  "founded": "1988-09-30",
  "suframa": [],
  "updated": "2025-12-30T16:27:18.000Z",
  "statusDate": "2005-11-03",
  "mainActivity": {
    "id": 6422100,
    "text": "Bancos múltiplos, com carteira comercial"
  },
  "sideActivities": []
}