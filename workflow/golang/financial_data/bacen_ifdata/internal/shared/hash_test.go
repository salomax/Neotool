package shared

import (
	"testing"
)

func TestComputeInstitutionHash_Consistency(t *testing.T) {
	fields := map[string]interface{}{
		"bacen_cod_inst": "12345",
		"nome_instituicao": "Test Bank",
		"situacao": "A",
		"segmento_tb": "B1",
		"atividade": "Commercial",
	}

	// Compute hash twice - should be identical
	hash1, err := ComputeInstitutionHash(fields)
	if err != nil {
		t.Fatalf("ComputeInstitutionHash() error = %v", err)
	}

	hash2, err := ComputeInstitutionHash(fields)
	if err != nil {
		t.Fatalf("ComputeInstitutionHash() error = %v", err)
	}

	if hash1 != hash2 {
		t.Errorf("hashes inconsistent: %s != %s", hash1, hash2)
	}

	// Hash should be 64 characters (SHA-256 hex)
	if len(hash1) != 64 {
		t.Errorf("hash length = %d, want 64", len(hash1))
	}
}

func TestComputeInstitutionHash_OrderIndependent(t *testing.T) {
	// Fields in different order should produce same hash
	fields1 := map[string]interface{}{
		"bacen_cod_inst": "12345",
		"nome_instituicao": "Test Bank",
		"situacao": "A",
	}

	fields2 := map[string]interface{}{
		"situacao": "A",
		"bacen_cod_inst": "12345",
		"nome_instituicao": "Test Bank",
	}

	hash1, err := ComputeInstitutionHash(fields1)
	if err != nil {
		t.Fatalf("ComputeInstitutionHash() error = %v", err)
	}

	hash2, err := ComputeInstitutionHash(fields2)
	if err != nil {
		t.Fatalf("ComputeInstitutionHash() error = %v", err)
	}

	if hash1 != hash2 {
		t.Errorf("hashes should be order-independent: %s != %s", hash1, hash2)
	}
}

func TestComputeInstitutionHash_WhitespaceNormalization(t *testing.T) {
	fields1 := map[string]interface{}{
		"bacen_cod_inst": "  12345  ",
		"nome_instituicao": "Test Bank",
	}

	fields2 := map[string]interface{}{
		"bacen_cod_inst": "12345",
		"nome_instituicao": "Test Bank",
	}

	hash1, err := ComputeInstitutionHash(fields1)
	if err != nil {
		t.Fatalf("ComputeInstitutionHash() error = %v", err)
	}

	hash2, err := ComputeInstitutionHash(fields2)
	if err != nil {
		t.Fatalf("ComputeInstitutionHash() error = %v", err)
	}

	if hash1 != hash2 {
		t.Errorf("hashes should normalize whitespace: %s != %s", hash1, hash2)
	}
}

func TestComputeInstitutionHash_EmptyStringsAsNil(t *testing.T) {
	fields1 := map[string]interface{}{
		"bacen_cod_inst": "12345",
		"segmento_tb": "",
	}

	fields2 := map[string]interface{}{
		"bacen_cod_inst": "12345",
		"segmento_tb": nil,
	}

	hash1, err := ComputeInstitutionHash(fields1)
	if err != nil {
		t.Fatalf("ComputeInstitutionHash() error = %v", err)
	}

	hash2, err := ComputeInstitutionHash(fields2)
	if err != nil {
		t.Fatalf("ComputeInstitutionHash() error = %v", err)
	}

	if hash1 != hash2 {
		t.Errorf("empty string and nil should hash the same: %s != %s", hash1, hash2)
	}
}

func TestComputeInstitutionHash_NullStringsAsNil(t *testing.T) {
	fields1 := map[string]interface{}{
		"bacen_cod_inst": "12345",
		"segmento_tb": "null",
	}

	fields2 := map[string]interface{}{
		"bacen_cod_inst": "12345",
		"segmento_tb": nil,
	}

	hash1, err := ComputeInstitutionHash(fields1)
	if err != nil {
		t.Fatalf("ComputeInstitutionHash() error = %v", err)
	}

	hash2, err := ComputeInstitutionHash(fields2)
	if err != nil {
		t.Fatalf("ComputeInstitutionHash() error = %v", err)
	}

	if hash1 != hash2 {
		t.Errorf("'null' string and nil should hash the same: %s != %s", hash1, hash2)
	}
}

func TestComputeInstitutionHash_NullStringsCaseInsensitive(t *testing.T) {
	fields1 := map[string]interface{}{
		"bacen_cod_inst": "12345",
		"segmento_tb": "NULL",
	}

	fields2 := map[string]interface{}{
		"bacen_cod_inst": "12345",
		"segmento_tb": "null",
	}

	fields3 := map[string]interface{}{
		"bacen_cod_inst": "12345",
		"segmento_tb": nil,
	}

	hash1, err := ComputeInstitutionHash(fields1)
	if err != nil {
		t.Fatalf("ComputeInstitutionHash() error = %v", err)
	}

	hash2, err := ComputeInstitutionHash(fields2)
	if err != nil {
		t.Fatalf("ComputeInstitutionHash() error = %v", err)
	}

	hash3, err := ComputeInstitutionHash(fields3)
	if err != nil {
		t.Fatalf("ComputeInstitutionHash() error = %v", err)
	}

	if hash1 != hash2 || hash2 != hash3 {
		t.Errorf("'NULL', 'null', and nil should all hash the same: %s != %s != %s", hash1, hash2, hash3)
	}
}

func TestComputeInstitutionHash_DifferentValues(t *testing.T) {
	fields1 := map[string]interface{}{
		"bacen_cod_inst": "12345",
		"nome_instituicao": "Test Bank A",
	}

	fields2 := map[string]interface{}{
		"bacen_cod_inst": "12345",
		"nome_instituicao": "Test Bank B",
	}

	hash1, err := ComputeInstitutionHash(fields1)
	if err != nil {
		t.Fatalf("ComputeInstitutionHash() error = %v", err)
	}

	hash2, err := ComputeInstitutionHash(fields2)
	if err != nil {
		t.Fatalf("ComputeInstitutionHash() error = %v", err)
	}

	if hash1 == hash2 {
		t.Errorf("different values should produce different hashes")
	}
}

func TestComputeInstitutionHash_EmptyMap(t *testing.T) {
	fields := map[string]interface{}{}

	hash, err := ComputeInstitutionHash(fields)
	if err != nil {
		t.Fatalf("ComputeInstitutionHash() error = %v", err)
	}

	if len(hash) != 64 {
		t.Errorf("hash length = %d, want 64", len(hash))
	}
}

func TestNormalizeString(t *testing.T) {
	tests := []struct {
		name  string
		input string
		want  *string
	}{
		{
			name:  "normal string",
			input: "test",
			want:  stringPtr("test"),
		},
		{
			name:  "string with leading/trailing whitespace",
			input: "  test  ",
			want:  stringPtr("test"),
		},
		{
			name:  "empty string",
			input: "",
			want:  nil,
		},
		{
			name:  "only whitespace",
			input: "   ",
			want:  nil,
		},
		{
			name:  "string with internal whitespace",
			input: "  hello world  ",
			want:  stringPtr("hello world"),
		},
		{
			name:  "null string",
			input: "null",
			want:  nil,
		},
		{
			name:  "null string with whitespace",
			input: "  null  ",
			want:  nil,
		},
		{
			name:  "NULL string (uppercase)",
			input: "NULL",
			want:  nil,
		},
		{
			name:  "Null string (mixed case)",
			input: "Null",
			want:  nil,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got := NormalizeString(tt.input)

			if tt.want == nil {
				if got != nil {
					t.Errorf("NormalizeString() = %v, want nil", *got)
				}
			} else {
				if got == nil {
					t.Errorf("NormalizeString() = nil, want %v", *tt.want)
				} else if *got != *tt.want {
					t.Errorf("NormalizeString() = %v, want %v", *got, *tt.want)
				}
			}
		})
	}
}

// Helper function to create string pointer
func stringPtr(s string) *string {
	return &s
}
