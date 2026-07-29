package http

import (
	"archive/zip"
	"io"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestOpenNamedCSVFromZip(t *testing.T) {
	zipPath := writeTestZip(t, map[string]string{
		"nested/registro_classe.csv": "classe-data\n",
		"REGISTRO_FUNDO.CSV":         "fundo-data\n",
		"registro_subclasse.csv":     "subclasse-data\n",
	})

	t.Run("opens each named csv independently", func(t *testing.T) {
		t.Parallel()

		zr, rc, err := OpenNamedCSVFromZip(zipPath, "registro_fundo.csv")
		if err != nil {
			t.Fatalf("OpenNamedCSVFromZip() error = %v", err)
		}
		defer zr.Close()
		defer rc.Close()

		data, err := io.ReadAll(rc)
		if err != nil {
			t.Fatalf("ReadAll() error = %v", err)
		}

		if got, want := string(data), "fundo-data\n"; got != want {
			t.Fatalf("fund csv contents = %q, want %q", got, want)
		}
	})

	t.Run("returns error when named entry is missing", func(t *testing.T) {
		t.Parallel()

		_, _, err := OpenNamedCSVFromZip(zipPath, "missing.csv")
		if err == nil {
			t.Fatal("OpenNamedCSVFromZip() error = nil, want missing entry error")
		}
		if !strings.Contains(err.Error(), "missing.csv") {
			t.Fatalf("OpenNamedCSVFromZip() error = %v, want entry name in message", err)
		}
	})
}

func writeTestZip(t *testing.T, entries map[string]string) string {
	t.Helper()

	tmpDir := t.TempDir()
	zipPath := filepath.Join(tmpDir, "fixture.zip")

	file, err := os.Create(zipPath)
	if err != nil {
		t.Fatalf("Create() error = %v", err)
	}

	zw := zip.NewWriter(file)
	for name, content := range entries {
		w, err := zw.Create(name)
		if err != nil {
			t.Fatalf("Create(%q) error = %v", name, err)
		}
		if _, err := w.Write([]byte(content)); err != nil {
			t.Fatalf("Write(%q) error = %v", name, err)
		}
	}

	if err := zw.Close(); err != nil {
		t.Fatalf("zip Close() error = %v", err)
	}
	if err := file.Close(); err != nil {
		t.Fatalf("file Close() error = %v", err)
	}

	return zipPath
}
