declare module 'react-input-mask' {
  import { ComponentType, InputHTMLAttributes } from 'react';

  export interface InputMaskProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'children'> {
    mask: string;
    maskChar?: string;
    formatChars?: Record<string, string>;
    alwaysShowMask?: boolean;
    beforeMaskedValueChange?: (newState: any, oldState: any, userInput: string, maskOptions: any) => any;
    children: (inputProps: any) => React.ReactNode;
  }

  const InputMask: ComponentType<InputMaskProps>;
  export default InputMask;
}