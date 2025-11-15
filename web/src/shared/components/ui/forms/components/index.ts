// web/src/components/form/index.ts
export * from "./FormLayout";
export * from "./FormSection";
export * from "./FormActions";
export * from "./FormErrorBanner";
export * from "./FormRow";

export * from "./fields/ControlledTextField";
export * from "./fields/ControlledNumberField";
export { default as ControlledCurrencyField } from "./fields/ControlledCurrencyField";
export * from "./fields/ControlledPercentField";
export * from "./fields/ControlledDatePicker";
export * from "./fields/ControlledSelect";
export * from "./fields/ControlledAutocomplete";
export * from "./fields/ControlledMaskedTextField";
export * from "./fields/ControlledSpecificMaskedFields";
export { default as ControlledSwitch } from "./fields/ControlledSwitch";
export { default as ControlledCheckbox } from "./fields/ControlledCheckbox";
export { default as ControlledRadioGroup } from "./fields/ControlledRadioGroup";
export { default as ControlledFileUpload } from "./fields/ControlledFileUpload";
export { default as ControlledAsyncValidateField } from "./fields/ControlledAsyncValidateField";

// Aliases "Controller*"
export { default as ControllerCurrencyField } from "./fields/ControlledCurrencyField";
export { ControlledPercentField as ControllerPercentField } from "./fields/ControlledPercentField";

export * as masksBR from "./masks/br";
export * as zodBR from "./validation/zodUtils";

export * from "./helpers/formHelpers";
export * from "./helpers/useDebouncedFieldValidator";
