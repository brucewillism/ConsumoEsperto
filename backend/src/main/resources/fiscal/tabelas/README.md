# Tabelas fiscais por ano

Esta pasta recebe arquivos JSON com faixas oficiais de INSS e IR por ano-calendário.

## Status

**BLOQUEADO EXTERNAMENTE** — os valores oficiais não estão versionados neste repositório.

## Formato esperado (`2025.json` exemplo)

```json
{
  "ano": 2025,
  "tetoInss": "8157.41",
  "deducaoDependente": "189.59",
  "fonte": "Receita Federal / INSS — publicação oficial",
  "faixasInss": [
    { "limiteSuperior": "1518.00", "aliquotaPct": "7.5" }
  ],
  "faixasIr": [
    { "limiteSuperior": "2259.20", "aliquotaPct": "0", "parcelaDeduzir": "0" }
  ]
}
```

## Uso no código

`com.consumoesperto.fiscal.TabelaFiscalAnoRegistry` carregará estes arquivos quando presentes.
Cálculos históricos devem usar o ano da competência, nunca a tabela do ano corrente.
