-- V0_24__update_category_icons_to_mui.sql
-- Update icon column for system seed categories from emoji to MUI icon names.
-- Uses name-based matching; applies only to system categories (is_system = true).

SET search_path TO banking, public;

UPDATE transaction_categories SET icon = 'payments'     WHERE name = 'Salário'                AND is_system = true;
UPDATE transaction_categories SET icon = 'work'         WHERE name = 'Renda Extra'            AND is_system = true;
UPDATE transaction_categories SET icon = 'elderly'      WHERE name = 'Aposentadoria / Pensão'          AND is_system = true;
UPDATE transaction_categories SET icon = 'trending_up'  WHERE name = 'Investimentos'          AND is_system = true;
UPDATE transaction_categories SET icon = 'call_received' WHERE name = 'Transferência recebida' AND is_system = true;
UPDATE transaction_categories SET icon = 'restaurant'   WHERE name = 'Alimentação'            AND is_system = true;
UPDATE transaction_categories SET icon = 'shopping_cart' WHERE name = 'Supermercado'          AND is_system = true;
UPDATE transaction_categories SET icon = 'ramen_dining' WHERE name = 'Restaurante'            AND is_system = true;
UPDATE transaction_categories SET icon = 'delivery_dining' WHERE name = 'Delivery'            AND is_system = true;
UPDATE transaction_categories SET icon = 'local_cafe'   WHERE name = 'Lanchonete'             AND is_system = true;
UPDATE transaction_categories SET icon = 'home'         WHERE name = 'Moradia'                AND is_system = true;
UPDATE transaction_categories SET icon = 'key'          WHERE name = 'Aluguel'                AND is_system = true;
UPDATE transaction_categories SET icon = 'apartment'    WHERE name = 'Condomínio'             AND is_system = true;
UPDATE transaction_categories SET icon = 'bolt'         WHERE name = 'Conta de luz'           AND is_system = true;
UPDATE transaction_categories SET icon = 'water_drop'   WHERE name = 'Água'                   AND is_system = true;
UPDATE transaction_categories SET icon = 'local_fire_department' WHERE name = 'Gás'           AND is_system = true;
UPDATE transaction_categories SET icon = 'directions_car' WHERE name = 'Transporte'           AND is_system = true;
UPDATE transaction_categories SET icon = 'local_gas_station' WHERE name = 'Combustível'       AND is_system = true;
UPDATE transaction_categories SET icon = 'directions_bus' WHERE name = 'Transporte público'   AND is_system = true;
UPDATE transaction_categories SET icon = 'smartphone'   WHERE name = 'Aplicativos (Uber)'     AND is_system = true;
UPDATE transaction_categories SET icon = 'local_hospital' WHERE name = 'Saúde'                AND is_system = true;
UPDATE transaction_categories SET icon = 'medication'   WHERE name = 'Farmácia'               AND is_system = true;
UPDATE transaction_categories SET icon = 'medical_services' WHERE name = 'Médico'             AND is_system = true;
UPDATE transaction_categories SET icon = 'health_and_safety' WHERE name = 'Plano de saúde'    AND is_system = true;
UPDATE transaction_categories SET icon = 'school'       WHERE name = 'Educação'               AND is_system = true;
UPDATE transaction_categories SET icon = 'account_balance' WHERE name = 'Mensalidade'         AND is_system = true;
UPDATE transaction_categories SET icon = 'menu_book'    WHERE name = 'Cursos'                 AND is_system = true;
UPDATE transaction_categories SET icon = 'edit'         WHERE name = 'Material escolar'       AND is_system = true;
UPDATE transaction_categories SET icon = 'celebration'  WHERE name = 'Lazer e Entretenimento'                  AND is_system = true;
UPDATE transaction_categories SET icon = 'movie'        WHERE name = 'Cinema / Teatro'         AND is_system = true;
UPDATE transaction_categories SET icon = 'sports_esports' WHERE name = 'Games'                AND is_system = true;
UPDATE transaction_categories SET icon = 'fitness_center' WHERE name = 'Academia'             AND is_system = true;
UPDATE transaction_categories SET icon = 'flight'       WHERE name = 'Viagem'                 AND is_system = true;
UPDATE transaction_categories SET icon = 'flight'       WHERE name = 'Viagens'                AND is_system = true;
UPDATE transaction_categories SET icon = 'shopping_bag' WHERE name = 'Compras'                AND is_system = true;
UPDATE transaction_categories SET icon = 'checkroom'    WHERE name = 'Roupas'                 AND is_system = true;
UPDATE transaction_categories SET icon = 'devices'      WHERE name = 'Eletrônicos'            AND is_system = true;
UPDATE transaction_categories SET icon = 'chair'        WHERE name = 'Casa / Decoração'       AND is_system = true;
UPDATE transaction_categories SET icon = 'live_tv'      WHERE name = 'Serviços Digitais'      AND is_system = true;
UPDATE transaction_categories SET icon = 'headphones'   WHERE name = 'Streaming'              AND is_system = true;
UPDATE transaction_categories SET icon = 'autorenew'    WHERE name = 'Assinaturas'            AND is_system = true;
UPDATE transaction_categories SET icon = 'cell_tower'   WHERE name = 'Telecomunicações'       AND is_system = true;
UPDATE transaction_categories SET icon = 'wifi'         WHERE name = 'Internet'               AND is_system = true;
UPDATE transaction_categories SET icon = 'phone_iphone' WHERE name = 'Celular'                AND is_system = true;
UPDATE transaction_categories SET icon = 'shield'       WHERE name = 'Seguros'                AND is_system = true;
UPDATE transaction_categories SET icon = 'account_balance' WHERE name = 'Impostos'            AND is_system = true;
UPDATE transaction_categories SET icon = 'receipt_long' WHERE name = 'IPTU / IPVA'            AND is_system = true;
UPDATE transaction_categories SET icon = 'bar_chart'    WHERE name = 'IR'                     AND is_system = true;
UPDATE transaction_categories SET icon = 'account_balance_wallet' WHERE name = 'Tarifas Bancárias' AND is_system = true;
UPDATE transaction_categories SET icon = 'credit_card'  WHERE name = 'Empréstimos'            AND is_system = true;
UPDATE transaction_categories SET icon = 'favorite'     WHERE name = 'Doações'                AND is_system = true;
UPDATE transaction_categories SET icon = 'call_made'    WHERE name = 'Transferência enviada'  AND is_system = true;
UPDATE transaction_categories SET icon = 'savings'      WHERE name = 'Investimento aplicado'  AND is_system = true;
UPDATE transaction_categories SET icon = 'help_outline' WHERE name = 'Outros'                 AND is_system = true;

-- System child categories without icon inherit the icon from their parent.
UPDATE transaction_categories child
SET icon = parent.icon, color = parent.color
FROM transaction_categories parent
WHERE child.parent_id = parent.id
  AND child.is_system = true
  AND parent.is_system = true
  AND child.icon IS NULL
  AND parent.icon IS NOT NULL;

UPDATE transaction_categories child
SET color = parent.color
FROM transaction_categories parent
WHERE child.parent_id = parent.id
  AND child.is_system = true
  AND parent.is_system = true
  AND child.color IS NULL
  AND parent.color IS NOT NULL;
