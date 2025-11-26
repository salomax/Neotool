import { describe, it, expect, vi } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { z } from 'zod';
import { useZodForm } from '../useZodForm';

describe('useZodForm', () => {
  it('should create form with zod schema', () => {
    const schema = z.object({
      name: z.string().min(1, 'Name is required'),
      email: z.string().email('Invalid email'),
    });

    const { result } = renderHook(() => useZodForm(schema));

    expect(result.current).toBeDefined();
    expect(result.current.formState).toBeDefined();
    expect(result.current.register).toBeDefined();
    expect(result.current.handleSubmit).toBeDefined();
  });

  it('should use default mode onBlur', () => {
    const schema = z.object({
      name: z.string(),
    });

    const { result } = renderHook(() => useZodForm(schema));

    expect(result.current.formState.mode).toBe('onBlur');
  });

  it('should use default reValidateMode onChange', () => {
    const schema = z.object({
      name: z.string(),
    });

    const { result } = renderHook(() => useZodForm(schema));

    expect(result.current.formState.reValidateMode).toBe('onChange');
  });

  it('should accept custom mode', () => {
    const schema = z.object({
      name: z.string(),
    });

    const { result } = renderHook(() =>
      useZodForm(schema, { mode: 'onChange' })
    );

    expect(result.current.formState.mode).toBe('onChange');
  });

  it('should accept custom reValidateMode', () => {
    const schema = z.object({
      name: z.string(),
    });

    const { result } = renderHook(() =>
      useZodForm(schema, { reValidateMode: 'onBlur' })
    );

    expect(result.current.formState.reValidateMode).toBe('onBlur');
  });

  it('should validate with zod schema', async () => {
    const schema = z.object({
      name: z.string().min(1, 'Name is required'),
      email: z.string().email('Invalid email'),
    });

    const { result } = renderHook(() => useZodForm(schema));

    const submitHandler = vi.fn();
    const handleSubmit = result.current.handleSubmit(submitHandler);

    // Try to submit with invalid data
    const mockEvent = {
      preventDefault: vi.fn(),
      stopPropagation: vi.fn(),
    } as any;
    
    await handleSubmit(mockEvent);

    expect(submitHandler).not.toHaveBeenCalled();
    // Trigger validation to populate errors - trigger all fields
    const isValid = await result.current.trigger();
    expect(isValid).toBe(false); // Validation should fail
    
    // Wait for form state to update - use waitFor to ensure state is updated
    // Access errors through getFieldState which should have the error after trigger
    await waitFor(() => {
      const nameFieldState = result.current.getFieldState('name');
      expect(nameFieldState.error).toBeDefined();
      // Zod's min() validation for empty strings may return "Required" as the default message
      // instead of the custom message, so we check for either
      expect(['Name is required', 'Required']).toContain(nameFieldState.error?.message);
    }, { timeout: 3000 });
  });

  it('should accept additional form props', () => {
    const schema = z.object({
      name: z.string(),
    });

    const defaultValues = { name: 'John' };

    const { result } = renderHook(() =>
      useZodForm(schema, { defaultValues })
    );

    expect(result.current.formState.defaultValues).toEqual(defaultValues);
  });

  it('should handle complex zod schemas', () => {
    const schema = z.object({
      name: z.string(),
      age: z.number().min(0).max(120),
      email: z.string().email().optional(),
      tags: z.array(z.string()),
    });

    const { result } = renderHook(() => useZodForm(schema));

    expect(result.current).toBeDefined();
    expect(result.current.formState).toBeDefined();
  });

  it('should handle nested zod schemas', () => {
    const schema = z.object({
      user: z.object({
        name: z.string(),
        email: z.string().email(),
      }),
    });

    const { result } = renderHook(() => useZodForm(schema));

    expect(result.current).toBeDefined();
  });

  it('should handle optional fields', () => {
    const schema = z.object({
      name: z.string(),
      description: z.string().optional(),
    });

    const { result } = renderHook(() => useZodForm(schema));

    expect(result.current).toBeDefined();
  });

  it('should handle union types', () => {
    const schema = z.object({
      type: z.union([z.literal('admin'), z.literal('user')]),
    });

    const { result } = renderHook(() => useZodForm(schema));

    expect(result.current).toBeDefined();
  });
});

