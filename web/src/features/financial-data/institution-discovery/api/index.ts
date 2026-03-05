import { gql } from "@apollo/client";

export const GET_POPULAR_INSTITUTIONS = gql`
  query GetPopularInstitutions($anoMes: String) {
    popularInstitutions(anoMes: $anoMes) {
      rank
      quarter
      year
      month
      institution {
        id
        name
        legalName
        tradeName
        bacenInstitutionType
        institutionType
        tipoConsolidadoBancario
        logoAsset {
            id
            publicUrl
        }
        dataRestriction {
          isRestricted
          reason
        }
      }
      popularInstitutionTopMetrics {
        metric
        popularInstitutionTopMetricItem {
          rank
          metricValue
          institution {
            id
            name
            tradeName
            logoAsset {
                id
                publicUrl
            }
            dataRestriction {
              isRestricted
              reason
            }
          }
        }
      }
    }
  }
`;
