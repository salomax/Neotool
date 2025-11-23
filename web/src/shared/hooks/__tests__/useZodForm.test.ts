import { describe, it, expect, vi } from 'vitest';
import { renderHook } from '@testing-library/react';
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
    await handleSubmit({
      preventDefault: vi.fn(),
      stopPropagation: vi.fn(),
    } as any);

    expect(submitHandler).not.toHaveBeenCalled();
    expect(result.current.formState.errors.name).toBeDefined();
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

