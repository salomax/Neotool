/**
 * Financial Institution Entity
 *
 * Business domain entity representing BACEN financial institutions.
 * Contains types, constants, and utilities related to financial institutions.
 */

// Constants
export {
  BASILEIA_THRESHOLDS,
  IMMOBILIZATION_THRESHOLDS
} from "./constants/metrics";

// Utils
export {
  getInstitutionTypeConfig,
  type BacenInstitutionType,
  type InstitutionTypeConfig,
} from "./utils/institutionType";
