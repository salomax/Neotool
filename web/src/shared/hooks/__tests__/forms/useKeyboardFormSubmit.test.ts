import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook } from '@testing-library/react';
import { useKeyboardFormSubmit } from '@/shared/hooks/forms';
import * as React from 'react';

describe('useKeyboardFormSubmit', () => {
  let container: HTMLDivElement;
  let input: HTMLInputElement;
  let mockOnSubmit: ReturnType<typeof vi.fn>;
  let mockIsSubmitEnabled: ReturnType<typeof vi.fn>;
  let containerRef: React.RefObject<HTMLDivElement>;

  beforeEach(() => {
    // Setup DOM
    container = document.createElement('div');
    document.body.appendChild(container);

    input = document.createElement('input');
    input.type = 'text';
    container.appendChild(input);

    // Setup mocks
    mockOnSubmit = vi.fn();
    mockIsSubmitEnabled = vi.fn(() => true);
    containerRef = React.createRef<HTMLDivElement>();
    containerRef.current = container;

    // Mock console.error to avoid cluttering test output
    vi.spyOn(console, 'error').mockImplementation(() => {});
  });

  afterEach(() => {
    document.body.removeChild(container);
    vi.restoreAllMocks();
  });

  describe('basic functionality', () => {
    it('should submit form when Enter is pressed in input', () => {
      renderHook(() =>
        useKeyboardFormSubmit({
          onSubmit: mockOnSubmit,
          isSubmitEnabled: mockIsSubmitEnabled,
          containerRef,
        })
      );

      input.focus();
      const event = new KeyboardEvent('keydown', {
        key: 'Enter',
        bubbles: true,
        cancelable: true,
      });
      input.dispatchEvent(event);

      expect(mockOnSubmit).toHaveBeenCalledTimes(1);
      expect(event.defaultPrevented).toBe(true);
    });

    it('should not submit when Shift+Enter is pressed', () => {
      renderHook(() =>
        useKeyboardFormSubmit({
          onSubmit: mockOnSubmit,
          isSubmitEnabled: mockIsSubmitEnabled,
          containerRef,
        })
      );

      input.focus();
      const event = new KeyboardEvent('keydown', {
        key: 'Enter',
        shiftKey: true,
        bubbles: true,
        cancelable: true,
      });
      input.dispatchEvent(event);

      expect(mockOnSubmit).not.toHaveBeenCalled();
      expect(event.defaultPrevented).toBe(false);
    });

    it('should not submit when other keys are pressed', () => {
      renderHook(() =>
        useKeyboardFormSubmit({
          onSubmit: mockOnSubmit,
          isSubmitEnabled: mockIsSubmitEnabled,
          containerRef,
        })
      );

      input.focus();
      const event = new KeyboardEvent('keydown', {
        key: 'Space',
        bubbles: true,
        cancelable: true,
      });
      input.dispatchEvent(event);

      expect(mockOnSubmit).not.toHaveBeenCalled();
    });
  });

  describe('input types', () => {
    it('should work with INPUT elements', () => {
      renderHook(() =>
        useKeyboardFormSubmit({
          onSubmit: mockOnSubmit,
          isSubmitEnabled: mockIsSubmitEnabled,
          containerRef,
        })
      );

      input.focus();
      input.dispatchEvent(
        new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true })
      );

      expect(mockOnSubmit).toHaveBeenCalled();
    });

    it('should work with TEXTAREA elements', () => {
      const textarea = document.createElement('textarea');
      container.appendChild(textarea);

      renderHook(() =>
        useKeyboardFormSubmit({
          onSubmit: mockOnSubmit,
          isSubmitEnabled: mockIsSubmitEnabled,
          containerRef,
        })
      );

      textarea.focus();
      textarea.dispatchEvent(
        new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true })
      );

      expect(mockOnSubmit).toHaveBeenCalled();
    });

    it('should work with SELECT elements', () => {
      const select = document.createElement('select');
      container.appendChild(select);

      renderHook(() =>
        useKeyboardFormSubmit({
          onSubmit: mockOnSubmit,
          isSubmitEnabled: mockIsSubmitEnabled,
          containerRef,
        })
      );

      select.focus();
      select.dispatchEvent(
        new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true })
      );

      expect(mockOnSubmit).toHaveBeenCalled();
    });

    it('should work with contenteditable elements', () => {
      const div = document.createElement('div');
      div.setAttribute('contenteditable', 'true');
      container.appendChild(div);

      renderHook(() =>
        useKeyboardFormSubmit({
          onSubmit: mockOnSubmit,
          isSubmitEnabled: mockIsSubmitEnabled,
          containerRef,
        })
      );

      div.focus();
      div.dispatchEvent(
        new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true })
      );

      expect(mockOnSubmit).toHaveBeenCalled();
    });

    it('should work with ARIA textbox role', () => {
      const div = document.createElement('div');
      div.setAttribute('role', 'textbox');
      container.appendChild(div);

      renderHook(() =>
        useKeyboardFormSubmit({
          onSubmit: mockOnSubmit,
          isSubmitEnabled: mockIsSubmitEnabled,
          containerRef,
        })
      );

      div.focus();
      div.dispatchEvent(
        new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true })
      );

      expect(mockOnSubmit).toHaveBeenCalled();
    });

    it('should not work with non-form elements', () => {
      const div = document.createElement('div');
      container.appendChild(div);

      renderHook(() =>
        useKeyboardFormSubmit({
          onSubmit: mockOnSubmit,
          isSubmitEnabled: mockIsSubmitEnabled,
          containerRef,
        })
      );

      div.focus();
      div.dispatchEvent(
        new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true })
      );

      expect(mockOnSubmit).not.toHaveBeenCalled();
    });
  });

  describe('disabled and readonly inputs', () => {
    it('should not submit when input is disabled', () => {
      input.disabled = true;

      renderHook(() =>
        useKeyboardFormSubmit({
          onSubmit: mockOnSubmit,
          isSubmitEnabled: mockIsSubmitEnabled,
          containerRef,
        })
      );

      input.focus();
      input.dispatchEvent(
        new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true })
      );

      expect(mockOnSubmit).not.toHaveBeenCalled();
    });

    it('should not submit when input is readonly', () => {
      input.readOnly = true;

      renderHook(() =>
        useKeyboardFormSubmit({
          onSubmit: mockOnSubmit,
          isSubmitEnabled: mockIsSubmitEnabled,
          containerRef,
        })
      );

      input.focus();
      input.dispatchEvent(
        new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true })
      );

      expect(mockOnSubmit).not.toHaveBeenCalled();
    });

    it('should not submit when textarea is disabled', () => {
      const textarea = document.createElement('textarea');
      textarea.disabled = true;
      container.appendChild(textarea);

      renderHook(() =>
        useKeyboardFormSubmit({
          onSubmit: mockOnSubmit,
          isSubmitEnabled: mockIsSubmitEnabled,
          containerRef,
        })
      );

      textarea.focus();
      textarea.dispatchEvent(
        new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true })
      );

      expect(mockOnSubmit).not.toHaveBeenCalled();
    });

    it('should not submit when select is disabled', () => {
      const select = document.createElement('select');
      select.disabled = true;
      container.appendChild(select);

      renderHook(() =>
        useKeyboardFormSubmit({
          onSubmit: mockOnSubmit,
          isSubmitEnabled: mockIsSubmitEnabled,
          containerRef,
        })
      );

      select.focus();
      select.dispatchEvent(
        new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true })
      );

      expect(mockOnSubmit).not.toHaveBeenCalled();
    });
  });

  describe('button elements', () => {
    it('should not intercept button presses', () => {
      const button = document.createElement('button');
      container.appendChild(button);

      renderHook(() =>
        useKeyboardFormSubmit({
          onSubmit: mockOnSubmit,
          isSubmitEnabled: mockIsSubmitEnabled,
          containerRef,
        })
      );

      button.focus();
      button.dispatchEvent(
        new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true })
      );

      expect(mockOnSubmit).not.toHaveBeenCalled();
    });
  });

  describe('dropdown and autocomplete detection', () => {
    it('should not submit when dropdown is open (aria-expanded)', () => {
      input.setAttribute('aria-expanded', 'true');

      renderHook(() =>
        useKeyboardFormSubmit({
          onSubmit: mockOnSubmit,
          isSubmitEnabled: mockIsSubmitEnabled,
          containerRef,
        })
      );

      input.focus();
      input.dispatchEvent(
        new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true })
      );

      expect(mockOnSubmit).not.toHaveBeenCalled();
    });

    it('should not submit when inside listbox', () => {
      const listbox = document.createElement('div');
      listbox.setAttribute('role', 'listbox');
      listbox.appendChild(input);
      container.appendChild(listbox);

      renderHook(() =>
        useKeyboardFormSubmit({
          onSubmit: mockOnSubmit,
          isSubmitEnabled: mockIsSubmitEnabled,
          containerRef,
        })
      );

      input.focus();
      input.dispatchEvent(
        new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true })
      );

      expect(mockOnSubmit).not.toHaveBeenCalled();
    });

    it('should not submit when inside menu', () => {
      const menu = document.createElement('div');
      menu.setAttribute('role', 'menu');
      menu.appendChild(input);
      container.appendChild(menu);

      renderHook(() =>
        useKeyboardFormSubmit({
          onSubmit: mockOnSubmit,
          isSubmitEnabled: mockIsSubmitEnabled,
          containerRef,
        })
      );

      input.focus();
      input.dispatchEvent(
        new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true })
      );

      expect(mockOnSubmit).not.toHaveBeenCalled();
    });

    it('should not submit when inside expanded combobox', () => {
      const combobox = document.createElement('div');
      combobox.setAttribute('role', 'combobox');
      combobox.setAttribute('aria-expanded', 'true');
      combobox.appendChild(input);
      container.appendChild(combobox);

      renderHook(() =>
        useKeyboardFormSubmit({
          onSubmit: mockOnSubmit,
          isSubmitEnabled: mockIsSubmitEnabled,
          containerRef,
        })
      );

      input.focus();
      input.dispatchEvent(
        new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true })
      );

      expect(mockOnSubmit).not.toHaveBeenCalled();
    });
  });

  describe('container scoping', () => {
    it('should only submit when focus is within container', () => {
      const outsideContainer = document.createElement('div');
      const outsideInput = document.createElement('input');
      outsideContainer.appendChild(outsideInput);
      document.body.appendChild(outsideContainer);

      renderHook(() =>
        useKeyboardFormSubmit({
          onSubmit: mockOnSubmit,
          isSubmitEnabled: mockIsSubmitEnabled,
          containerRef,
        })
      );

      outsideInput.focus();
      outsideInput.dispatchEvent(
        new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true })
      );

      expect(mockOnSubmit).not.toHaveBeenCalled();

      document.body.removeChild(outsideContainer);
    });

    it('should submit when focus is within container', () => {
      renderHook(() =>
        useKeyboardFormSubmit({
          onSubmit: mockOnSubmit,
          isSubmitEnabled: mockIsSubmitEnabled,
          containerRef,
        })
      );

      input.focus();
      input.dispatchEvent(
        new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true })
      );

      expect(mockOnSubmit).toHaveBeenCalled();
    });

    it('should wait for container ref to be available', () => {
      const emptyRef = React.createRef<HTMLDivElement>();

      renderHook(() =>
        useKeyboardFormSubmit({
          onSubmit: mockOnSubmit,
          isSubmitEnabled: mockIsSubmitEnabled,
          containerRef: emptyRef,
        })
      );

      input.focus();
      input.dispatchEvent(
        new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true })
      );

      expect(mockOnSubmit).not.toHaveBeenCalled();
    });
  });

  describe('enabled state', () => {
    it('should not submit when hook is disabled', () => {
      renderHook(() =>
        useKeyboardFormSubmit({
          onSubmit: mockOnSubmit,
          isSubmitEnabled: mockIsSubmitEnabled,
          containerRef,
          enabled: false,
        })
      );

      input.focus();
      input.dispatchEvent(
        new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true })
      );

      expect(mockOnSubmit).not.toHaveBeenCalled();
    });

    it('should submit when hook is enabled', () => {
      renderHook(() =>
        useKeyboardFormSubmit({
          onSubmit: mockOnSubmit,
          isSubmitEnabled: mockIsSubmitEnabled,
          containerRef,
          enabled: true,
        })
      );

      input.focus();
      input.dispatchEvent(
        new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true })
      );

      expect(mockOnSubmit).toHaveBeenCalled();
    });

    it('should update when enabled state changes', () => {
      const { rerender } = renderHook(
        ({ enabled }) =>
          useKeyboardFormSubmit({
            onSubmit: mockOnSubmit,
            isSubmitEnabled: mockIsSubmitEnabled,
            containerRef,
            enabled,
          }),
        {
          initialProps: { enabled: false },
        }
      );

      input.focus();
      input.dispatchEvent(
        new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true })
      );
      expect(mockOnSubmit).not.toHaveBeenCalled();

      rerender({ enabled: true });
      input.dispatchEvent(
        new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true })
      );
      expect(mockOnSubmit).toHaveBeenCalled();
    });
  });

  describe('isSubmitEnabled check', () => {
    it('should not submit when isSubmitEnabled returns false', () => {
      mockIsSubmitEnabled.mockReturnValue(false);

      renderHook(() =>
        useKeyboardFormSubmit({
          onSubmit: mockOnSubmit,
          isSubmitEnabled: mockIsSubmitEnabled,
          containerRef,
        })
      );

      input.focus();
      input.dispatchEvent(
        new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true })
      );

      expect(mockOnSubmit).not.toHaveBeenCalled();
    });

    it('should submit when isSubmitEnabled returns true', () => {
      mockIsSubmitEnabled.mockReturnValue(true);

      renderHook(() =>
        useKeyboardFormSubmit({
          onSubmit: mockOnSubmit,
          isSubmitEnabled: mockIsSubmitEnabled,
          containerRef,
        })
      );

      input.focus();
      input.dispatchEvent(
        new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true })
      );

      expect(mockOnSubmit).toHaveBeenCalled();
    });

    it('should use latest isSubmitEnabled function', () => {
      const { rerender } = renderHook(
        ({ isSubmitEnabled }) =>
          useKeyboardFormSubmit({
            onSubmit: mockOnSubmit,
            isSubmitEnabled,
            containerRef,
          }),
        {
          initialProps: { isSubmitEnabled: () => false },
        }
      );

      input.focus();
      input.dispatchEvent(
        new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true })
      );
      expect(mockOnSubmit).not.toHaveBeenCalled();

      rerender({ isSubmitEnabled: () => true });
      input.dispatchEvent(
        new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true })
      );
      expect(mockOnSubmit).toHaveBeenCalled();
    });
  });

  describe('nested forms', () => {
    it('should submit when form is within container', () => {
      const form = document.createElement('form');
      form.appendChild(input);
      container.appendChild(form);

      renderHook(() =>
        useKeyboardFormSubmit({
          onSubmit: mockOnSubmit,
          isSubmitEnabled: mockIsSubmitEnabled,
          containerRef,
        })
      );

      input.focus();
      input.dispatchEvent(
        new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true })
      );

      expect(mockOnSubmit).toHaveBeenCalled();
    });

    it('should not submit when form is outside container', () => {
      const outsideForm = document.createElement('form');
      const outsideInput = document.createElement('input');
      outsideForm.appendChild(outsideInput);
      document.body.appendChild(outsideForm);

      renderHook(() =>
        useKeyboardFormSubmit({
          onSubmit: mockOnSubmit,
          isSubmitEnabled: mockIsSubmitEnabled,
          containerRef,
        })
      );

      outsideInput.focus();
      outsideInput.dispatchEvent(
        new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true })
      );

      expect(mockOnSubmit).not.toHaveBeenCalled();

      document.body.removeChild(outsideForm);
    });

    it('should submit when no form element exists (react-hook-form pattern)', () => {
      // Input without form element
      renderHook(() =>
        useKeyboardFormSubmit({
          onSubmit: mockOnSubmit,
          isSubmitEnabled: mockIsSubmitEnabled,
          containerRef,
        })
      );

      input.focus();
      input.dispatchEvent(
        new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true })
      );

      expect(mockOnSubmit).toHaveBeenCalled();
    });
  });

  describe('error handling', () => {
    it('should handle synchronous errors', () => {
      const error = new Error('Submission failed');
      mockOnSubmit.mockImplementation(() => {
        throw error;
      });

      renderHook(() =>
        useKeyboardFormSubmit({
          onSubmit: mockOnSubmit,
          isSubmitEnabled: mockIsSubmitEnabled,
          containerRef,
        })
      );

      input.focus();
      input.dispatchEvent(
        new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true })
      );

      expect(mockOnSubmit).toHaveBeenCalled();
      expect(console.error).toHaveBeenCalledWith('Form submission error:', error);
    });

    it('should handle async errors', async () => {
      const error = new Error('Async submission failed');
      mockOnSubmit.mockRejectedValue(error);

      renderHook(() =>
        useKeyboardFormSubmit({
          onSubmit: mockOnSubmit,
          isSubmitEnabled: mockIsSubmitEnabled,
          containerRef,
        })
      );

      input.focus();
      input.dispatchEvent(
        new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true })
      );

      await new Promise((resolve) => setTimeout(resolve, 0));

      expect(mockOnSubmit).toHaveBeenCalled();
      expect(console.error).toHaveBeenCalledWith('Form submission error:', error);
    });

    it('should handle successful async submission', async () => {
      mockOnSubmit.mockResolvedValue(undefined);

      renderHook(() =>
        useKeyboardFormSubmit({
          onSubmit: mockOnSubmit,
          isSubmitEnabled: mockIsSubmitEnabled,
          containerRef,
        })
      );

      input.focus();
      input.dispatchEvent(
        new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true })
      );

      await new Promise((resolve) => setTimeout(resolve, 0));

      expect(mockOnSubmit).toHaveBeenCalled();
      expect(console.error).not.toHaveBeenCalled();
    });
  });

  describe('event prevention', () => {
    it('should prevent default and stop propagation when submitting', () => {
      renderHook(() =>
        useKeyboardFormSubmit({
          onSubmit: mockOnSubmit,
          isSubmitEnabled: mockIsSubmitEnabled,
          containerRef,
        })
      );

      input.focus();
      const event = new KeyboardEvent('keydown', {
        key: 'Enter',
        bubbles: true,
        cancelable: true,
      });
      const stopPropagationSpy = vi.spyOn(event, 'stopPropagation');
      input.dispatchEvent(event);

      expect(event.defaultPrevented).toBe(true);
      expect(stopPropagationSpy).toHaveBeenCalled();
    });

    it('should not prevent default when not submitting', () => {
      mockIsSubmitEnabled.mockReturnValue(false);

      renderHook(() =>
        useKeyboardFormSubmit({
          onSubmit: mockOnSubmit,
          isSubmitEnabled: mockIsSubmitEnabled,
          containerRef,
        })
      );

      input.focus();
      const event = new KeyboardEvent('keydown', {
        key: 'Enter',
        bubbles: true,
        cancelable: true,
      });
      input.dispatchEvent(event);

      expect(event.defaultPrevented).toBe(false);
    });
  });

  describe('cleanup', () => {
    it('should remove event listener on unmount', () => {
      const removeEventListenerSpy = vi.spyOn(container, 'removeEventListener');

      const { unmount } = renderHook(() =>
        useKeyboardFormSubmit({
          onSubmit: mockOnSubmit,
          isSubmitEnabled: mockIsSubmitEnabled,
          containerRef,
        })
      );

      unmount();

      expect(removeEventListenerSpy).toHaveBeenCalled();
    });

    it('should not submit after unmount', () => {
      const { unmount } = renderHook(() =>
        useKeyboardFormSubmit({
          onSubmit: mockOnSubmit,
          isSubmitEnabled: mockIsSubmitEnabled,
          containerRef,
        })
      );

      unmount();

      input.focus();
      input.dispatchEvent(
        new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true })
      );

      expect(mockOnSubmit).not.toHaveBeenCalled();
    });
  });

  describe('onSubmit function updates', () => {
    it('should use latest onSubmit function', () => {
      const firstOnSubmit = vi.fn();
      const secondOnSubmit = vi.fn();

      const { rerender } = renderHook(
        ({ onSubmit }) =>
          useKeyboardFormSubmit({
            onSubmit,
            isSubmitEnabled: mockIsSubmitEnabled,
            containerRef,
          }),
        {
          initialProps: { onSubmit: firstOnSubmit },
        }
      );

      input.focus();
      input.dispatchEvent(
        new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true })
      );
      expect(firstOnSubmit).toHaveBeenCalled();
      expect(secondOnSubmit).not.toHaveBeenCalled();

      firstOnSubmit.mockClear();

      rerender({ onSubmit: secondOnSubmit });
      input.dispatchEvent(
        new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true })
      );
      expect(secondOnSubmit).toHaveBeenCalled();
      expect(firstOnSubmit).not.toHaveBeenCalled();
    });
  });

  describe('edge cases', () => {
    it('should handle when activeElement is null', () => {
      // Simulate no active element
      // eslint-disable-next-line testing-library/no-node-access
      const originalActiveElement = document.activeElement;
      Object.defineProperty(document, 'activeElement', {
        value: null,
        writable: true,
        configurable: true,
      });

      renderHook(() =>
        useKeyboardFormSubmit({
          onSubmit: mockOnSubmit,
          isSubmitEnabled: mockIsSubmitEnabled,
          containerRef,
        })
      );

      container.dispatchEvent(
        new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true })
      );

      expect(mockOnSubmit).not.toHaveBeenCalled();

      // Restore
      // eslint-disable-next-line testing-library/no-node-access
      Object.defineProperty(document, 'activeElement', {
        value: originalActiveElement,
        writable: true,
        configurable: true,
      });
    });

    it('should handle non-KeyboardEvent events', () => {
      renderHook(() =>
        useKeyboardFormSubmit({
          onSubmit: mockOnSubmit,
          isSubmitEnabled: mockIsSubmitEnabled,
          containerRef,
        })
      );

      const event = new Event('keydown', { bubbles: true, cancelable: true });
      container.dispatchEvent(event);

      expect(mockOnSubmit).not.toHaveBeenCalled();
    });

    it('should handle multiple rapid submissions', () => {
      renderHook(() =>
        useKeyboardFormSubmit({
          onSubmit: mockOnSubmit,
          isSubmitEnabled: mockIsSubmitEnabled,
          containerRef,
        })
      );

      input.focus();
      for (let i = 0; i < 5; i++) {
        input.dispatchEvent(
          new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true })
        );
      }

      expect(mockOnSubmit).toHaveBeenCalledTimes(5);
    });
  });
});
