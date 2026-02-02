import { gql } from "@apollo/client";

export const GET_ECONOMIC_INDICATORS = gql`
  query GetEconomicIndicators {
    economicIndicators {
      id
      label
      description
      unit
      frequency
      variations {
        id
        label
        isDefault
      }
    }
  }
`;

export const GET_INDICATORS_LATEST = gql`
  query IndicatorsLatest {
    indicatorsLatest {
      indicator
      variation
      unit
      latest {
        referenceDate
        value
      }
    }
  }
`;
