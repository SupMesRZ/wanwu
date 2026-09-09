package i18n

import (
	"encoding/json"
	"flag"
	"os"
	"testing"

	"github.com/UnicomAI/wanwu/pkg/util"
)

var (
	yamlFile  string
	xlsxFile  string
	jsonlFile string
)

type YamlConfig struct {
	I18n Config `json:"i18n" mapstructure:"i18n"`
}

func TestMain(m *testing.M) {
	flag.StringVar(&yamlFile, "config", "../../configs/microservice/bff-service/configs/config.yaml", "conf yaml file")
	flag.StringVar(&xlsxFile, "xlsx", "../../configs/microservice/bff-service/configs/wanwu-i18n.xlsx", "i18n xlsx file")
	flag.StringVar(&jsonlFile, "jsonl", "../../configs/microservice/bff-service/configs/wanwu-i18n.jsonl", "i18n jsonl file")

	flag.Parse()
	os.Exit(m.Run())
}

func TestI18nConvertXlsx2Jsonl(t *testing.T) {
	if _, err := os.Stat(xlsxFile); os.IsNotExist(err) {
		t.Skipf("optional i18n source is unavailable: %s", xlsxFile)
	} else if err != nil {
		t.Fatal(err)
	}
	cfg := YamlConfig{}
	if err := util.LoadConfig(yamlFile, &cfg); err != nil {
		t.Fatal(err)
	}
	// load xlsx
	var langs []string
	for _, lang := range cfg.I18n.Langs {
		langs = append(langs, lang.Code)
	}
	textCfgs, err := loadXlsxTextConfigs(xlsxFile, cfg.I18n.XlsxSheets, langs)
	if err != nil {
		t.Fatal(err)
	}
	// create jsonl
	f, err := os.OpenFile(jsonlFile, os.O_CREATE|os.O_WRONLY|os.O_TRUNC, 0644)
	if err != nil {
		t.Fatal(err)
	}
	defer func() { _ = f.Close() }()
	// write jsonl
	encoder := json.NewEncoder(f)
	for _, textCfg := range textCfgs {
		if err := encoder.Encode(textCfg); err != nil {
			t.Fatal(err)
		}
	}
}
