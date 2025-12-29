/*
 * Migration: V1_7
 * Description: Alter institution table to add consolidation type fields (TCB, TD, TC)
 *              and remove nome_reduzido field
 *
 * Changes:
 * 1. Add tipo_consolidado_bancario (TCB) - Banking consolidated type
 * 2. Add tipo_consolidacao (TD) - Consolidation type
 * 3. Rename tipo_controle to clarify it holds TC values
 * 4. Remove nome_reduzido field (not meaningful from BACEN)
 *
 * BACEN Field Reference:
 * - TCB (Tipo de Consolidado Bancário): Banking consolidated type
 *   Values: b1, B2, B3S, B3C, b4, N1, n2, N4
 * - TD (Tipo de Consolidação): Consolidation type
 *   Values: I (Independent), C (Conglomerate)
 * - TC (Tipo de Controle): Control type
 *   Values: 1 (Public), 2 (Private national), 3 (Private foreign control)
 */

-- Add new columns for consolidation fields (idempotent - only if they don't exist)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_schema = 'financial_data' 
        AND table_name = 'institution' 
        AND column_name = 'tipo_consolidado_bancario'
    ) THEN
        ALTER TABLE financial_data.institution
            ADD COLUMN tipo_consolidado_bancario TEXT;
    END IF;
    
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_schema = 'financial_data' 
        AND table_name = 'institution' 
        AND column_name = 'tipo_consolidacao'
    ) THEN
        ALTER TABLE financial_data.institution
            ADD COLUMN tipo_consolidacao CHAR(1);
    END IF;
END $$;

-- Drop nome_reduzido column (not meaningful) - already idempotent with IF EXISTS
ALTER TABLE financial_data.institution
    DROP COLUMN IF EXISTS nome_reduzido;

-- Add comments for documentation
COMMENT ON COLUMN financial_data.institution.tipo_consolidado_bancario IS 'Banking consolidated type (TCB): b1=Commercial bank/Caixas, B2=Multiple/Investment, B3S=Singular cooperative, B3C=Central cooperative, b4=Development, N1=Non-bank credit, n2=Non-bank capital markets, N4=Payment institutions';
COMMENT ON COLUMN financial_data.institution.tipo_consolidacao IS 'Consolidation type (TD): I=Independent institution, C=Conglomerate';
COMMENT ON COLUMN financial_data.institution.tipo_controle IS 'Control type (TC): 1=Public, 2=Private national, 3=Private foreign control';

