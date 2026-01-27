/**
 * Search Institutions Feature
 *
 * Handles searching and browsing BACEN financial institutions.
 */

export {
  useBacenInstitutionsSearch,
  useBacenInstitutionsAutocompleteQuery,
  type BacenInstitutionSearchOption,
} from "./hooks/useBacenInstitutionsSearch";

export {
  BACEN_INSTITUTIONS,
  SEARCH_BACEN_INSTITUTIONS_AUTOCOMPLETE,
} from "./api";
