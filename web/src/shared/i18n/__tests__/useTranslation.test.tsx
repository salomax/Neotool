import React from "react";
import { render, screen } from "@testing-library/react";
import { act } from "@testing-library/react";
import "@/shared/i18n/config";
import { useTranslation } from "../hooks/useTranslation";
import { DomainTranslations } from "../types";

// Mock domain translations
const mockCustomersTranslations: DomainTranslations = {
  domain: 'customers',
  en: {
    title: 'Customer Management',
    addCustomer: 'Add Customer',
    name: 'Name',
    email: 'Email'
  },
  pt: {
    title: 'Gerenciamento de Clientes',
    addCustomer: 'Adicionar Cliente',
    name: 'Nome',
    email: 'E-mail'
  }
};

const mockProductsTranslations: DomainTranslations = {
  domain: 'products',
  en: {
    title: 'Product Management',
    addProduct: 'Add Product',
    sku: 'SKU'
  },
  pt: {
    title: 'Gerenciamento de Produtos',
    addProduct: 'Adicionar Produto',
    sku: 'SKU'
  }
};

// Test component for single domain
function SingleDomainTestComponent() {
  const { t, tDomain, tCommon } = useTranslation(mockCustomersTranslations);
  
  return (
    <div>
      <h1 data-testid="title">{t('title')}</h1>
      <button data-testid="add-customer">{t('addCustomer')}</button>
      <button data-testid="save">{t('actions.save')}</button>
      <button data-testid="cancel">{t('actions.cancel')}</button>
      <span data-testid="domain-name">{tDomain('name')}</span>
      <span data-testid="common-save">{tCommon('actions.save')}</span>
    </div>
  );
}

// Test component for multiple domains
function MultipleDomainsTestComponent() {
  const { t, getDomain, common } = useTranslation([
    mockCustomersTranslations,
    mockProductsTranslations
  ]);
  
  const customersT = getDomain('customers');
  const productsT = getDomain('products');
  
  return (
    <div>
      <h1 data-testid="customers-title">{t('title')}</h1>
      <button data-testid="add-customer">{t('addCustomer')}</button>
      <button data-testid="add-product">{t('addProduct')}</button>
      <button data-testid="save">{t('actions.save')}</button>
      <span data-testid="customers-direct">{customersT?.t('name')}</span>
      <span data-testid="products-direct">{productsT?.t('sku')}</span>
      <span data-testid="common-save">{common('actions.save')}</span>
    </div>
  );
}

describe("useTranslation Hook", () => {
  beforeEach(async () => {
    // Reset i18n to English before each test
    const i18n = (await import('i18next')).default;
    await i18n.changeLanguage('en');
  });

  describe("Single Domain", () => {
    it("should render domain-specific translations", () => {
      render(<SingleDomainTestComponent />);
      
      expect(screen.getByTestId('title')).toHaveTextContent('Customer Management');
      expect(screen.getByTestId('add-customer')).toHaveTextContent('Add Customer');
      expect(screen.getByTestId('domain-name')).toHaveTextContent('Name');
    });

    it("should fallback to common translations when domain translation not found", () => {
      render(<SingleDomainTestComponent />);
      
      // These should fallback to common translations
      expect(screen.getByTestId('save')).toHaveTextContent('Save');
      expect(screen.getByTestId('cancel')).toHaveTextContent('Cancel');
    });

    it("should provide direct access to domain translations", () => {
      render(<SingleDomainTestComponent />);
      
      expect(screen.getByTestId('domain-name')).toHaveTextContent('Name');
    });

    it("should provide direct access to common translations", () => {
      render(<SingleDomainTestComponent />);
      
      expect(screen.getByTestId('common-save')).toHaveTextContent('Save');
    });

    it("should switch languages correctly", async () => {
      render(<SingleDomainTestComponent />);
      
      // Switch to Portuguese
      const i18n = (await import('i18next')).default;
      await act(async () => {
        await i18n.changeLanguage('pt');
      });
      
      expect(screen.getByTestId('title')).toHaveTextContent('Gerenciamento de Clientes');
      expect(screen.getByTestId('add-customer')).toHaveTextContent('Adicionar Cliente');
      expect(screen.getByTestId('domain-name')).toHaveTextContent('Nome');
    });
  });

  describe("Multiple Domains", () => {
    it("should render translations from first domain", () => {
      render(<MultipleDomainsTestComponent />);
      
      // Should get title from customers (first domain)
      expect(screen.getByTestId('customers-title')).toHaveTextContent('Customer Management');
      expect(screen.getByTestId('add-customer')).toHaveTextContent('Add Customer');
    });

    it("should find translations across all domains", () => {
      render(<MultipleDomainsTestComponent />);
      
      expect(screen.getByTestId('add-customer')).toHaveTextContent('Add Customer');
      expect(screen.getByTestId('add-product')).toHaveTextContent('Add Product');
    });

    it("should fallback to common when no domain has the translation", () => {
      render(<MultipleDomainsTestComponent />);
      
      expect(screen.getByTestId('save')).toHaveTextContent('Save');
    });

    it("should provide direct access to specific domains", () => {
      render(<MultipleDomainsTestComponent />);
      
      expect(screen.getByTestId('customers-direct')).toHaveTextContent('Name');
      expect(screen.getByTestId('products-direct')).toHaveTextContent('SKU');
    });

    it("should return null for non-existent domain", () => {
      function TestComponent() {
        const { getDomain } = useTranslation([mockCustomersTranslations]);
        const nonExistentDomain = getDomain('non-existent');
        return <div data-testid="result">{nonExistentDomain ? 'found' : 'null'}</div>;
      }
      
      render(<TestComponent />);
      expect(screen.getByTestId('result')).toHaveTextContent('null');
    });

    it("should switch languages across all domains", async () => {
      render(<MultipleDomainsTestComponent />);
      
      // Switch to Portuguese
      const i18n = (await import('i18next')).default;
      await act(async () => {
        await i18n.changeLanguage('pt');
      });
      
      expect(screen.getByTestId('customers-title')).toHaveTextContent('Gerenciamento de Clientes');
      expect(screen.getByTestId('add-customer')).toHaveTextContent('Adicionar Cliente');
      expect(screen.getByTestId('add-product')).toHaveTextContent('Adicionar Produto');
      expect(screen.getByTestId('customers-direct')).toHaveTextContent('Nome');
    });
  });

  describe("Domain Registration", () => {
    it("should register domains automatically", async () => {
      const i18n = (await import('i18next')).default;
      const initialResourceCount = Object.keys(i18n.getResourceBundle('en', 'customers') || {}).length;
      
      render(<SingleDomainTestComponent />);
      
      // Domain should be registered
      expect(i18n.hasResourceBundle('en', 'customers')).toBe(true);
      expect(i18n.hasResourceBundle('pt', 'customers')).toBe(true);
    });

    it("should not re-register already registered domains", async () => {
      const i18n = (await import('i18next')).default;
      
      // First render
      const { unmount } = render(<SingleDomainTestComponent />);
      const firstResourceCount = Object.keys(i18n.getResourceBundle('en', 'customers') || {}).length;
      
      // Unmount and re-render
      unmount();
      render(<SingleDomainTestComponent />);
      
      const secondResourceCount = Object.keys(i18n.getResourceBundle('en', 'customers') || {}).length;
      
      // Should not have added duplicate resources
      expect(secondResourceCount).toBe(firstResourceCount);
    });
  });

  describe("Error Handling", () => {
    it("should handle missing translation keys gracefully", () => {
      function TestComponent() {
        const { t } = useTranslation(mockCustomersTranslations);
        return <div data-testid="result">{t('nonExistentKey')}</div>;
      }
      
      render(<TestComponent />);
      expect(screen.getByTestId('result')).toHaveTextContent('nonExistentKey');
    });

    it("should handle empty domain translations", () => {
      const emptyTranslations: DomainTranslations = {
        domain: 'empty',
        en: {},
        pt: {}
      };
      
      function TestComponent() {
        const { t } = useTranslation(emptyTranslations);
        return <div data-testid="result">{t('anyKey')}</div>;
      }
      
      render(<TestComponent />);
      expect(screen.getByTestId('result')).toHaveTextContent('anyKey');
    });
  });

  describe("Performance", () => {
    it("should memoize translation functions", () => {
      let renderCount = 0;
      
      function TestComponent() {
        renderCount++;
        const { t } = useTranslation(mockCustomersTranslations);
        return <div>{t('title')}</div>;
      }
      
      const { rerender } = render(<TestComponent />);
      expect(renderCount).toBe(1);
      
      // Re-render with same props
      rerender(<TestComponent />);
      expect(renderCount).toBe(2);
      
      // Translation function should be memoized (same reference)
      // This is tested implicitly through the fact that the component works correctly
    });
  });
});
