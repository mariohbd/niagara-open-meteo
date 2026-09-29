# Niagara Open-Meteo

Patch independente para usar **Open-Meteo no widget original do Niagara Launcher 1.16.28 (1634) e 1.16.29 (1635)**, através do Morphe.

**Versão 0.2.0: suporte adicionado à 1.16.29.** O usuário confirmou o funcionamento do clima na 1.16.28; a 1.16.29 passou nas verificações locais, mas ainda precisa de teste em aparelho Android/ART. O patch rejeita outras versões/builds e não desbloqueia o Niagara Pro.

## Funcionalidades

- Clima atual, sensação térmica e previsões horária e diária por sete dias.
- Parser, cache, widget e preferência Celsius/Fahrenheit originais do Niagara.
- Probabilidade de chuva e ícones de dia/noite.
- Descrições em português para `pt`/`pt_br`; inglês nos demais idiomas.
- Atribuição Open-Meteo (CC BY 4.0) na tela de clima.

Não oferece alertas de chuva minuto a minuto nem AQI. A lista de previsão por minuto fica vazia. Não modifica o calendário; a integração com Fossify depende dos eventos disponíveis no provedor de calendário do Android e ainda precisa de teste em aparelho.

As coordenadas já configuradas no clima do Niagara são enviadas por HTTPS a `api.open-meteo.com`, sem tokens da conta Niagara. Consulte as [condições da API Open-Meteo](https://open-meteo.com/en/docs); o endpoint gratuito é destinado a uso pessoal não comercial.

## Compilar

Requisitos: PowerShell 7, JDK 21 e acesso à internet para o primeiro download. Defina `JAVA_HOME` apontando para o JDK.

```powershell
./setup.ps1
./build.ps1 -RunTests
```

O script baixa ferramentas de versões fixas e verifica seus SHA-256. A saída é `dist/niagara-open-meteo-0.2.0.mpp`. Não é necessário fornecer APK nem credenciais do GitHub para compilar o pacote. O build não faz chamadas à API de clima; os testes usam uma fixture pública em (0,0).

O arquivo `docs/build-workflow.yml` é um exemplo opcional de GitHub Actions. Para habilitar CI, copie-o para `.github/workflows/build.yml` usando uma autenticação com permissão de workflows. Ele compila, testa e disponibiliza apenas o pacote MPP; não publica APKs nem chaves de assinatura.

## Usar junto com d0nj/morphe-patches

Não precisa fazer fork ou juntar os repositórios:

1. Mantenha [d0nj/morphe-patches](https://github.com/d0nj/morphe-patches) como fonte no Morphe.
2. Importe o `.mpp` deste projeto como outra fonte local.
3. Ative **Settings → Advanced → Expert mode** no Morphe Manager.
4. Selecione seu APK original **1.16.28 (1634) ou 1.16.29 (1635)** e escolha os patches nas abas das duas fontes.
5. Selecione **Open-Meteo weather** e gere o APK na mesma execução.

O pacote foi compilado contra Morphe Patcher **1.14.1**, incluído no Morphe Desktop **1.17.0**. Use uma versão compatível do Manager. O [modo Expert](https://github.com/MorpheApp/morphe-manager/blob/main/docs/patching-expert-mode.md) permite combinar fontes; o [Desktop](https://github.com/MorpheApp/morphe-desktop/blob/main/docs/documentation.md) aceita vários argumentos `-p`.

Exemplo de aplicação de apenas clima pelo Desktop:

```powershell
java -jar tools/morphe-desktop.jar patch original.apk -p dist/niagara-open-meteo-0.2.0.mpp --exclusive -e 'Open-Meteo weather' --bytecode-mode FULL -o niagara-open-meteo.apk
```

A validação da 1.16.29 usou o APK fornecido pelo usuário, que já continha patches d0nj v1.3.1. O patch acrescentou apenas clima e preservou o código preexistente. Essa validação não equivale a um teste com APK oficial limpo.

O APK resultante terá a assinatura do patcher e pode não instalar como atualização da versão oficial. Exporte seu backup do Niagara antes de substituir a instalação. Este repositório não fornece APKs do Niagara.

## Validação

- 136 verificações offline do contrato JSON: unidades, ícones, valores ausentes, dados antigos, coordenadas inválidas e horário de verão.
- Chamada HTTPS real do adaptador Java testada com coordenadas públicas (0,0).
- Aplicação, recompilação e assinatura via Morphe verificadas no APK alvo.
- Comparação simbólica do DEX: hook preserva o controle de fluxo original dos demais branches; 55.894 outros métodos na 1.16.28 e 55.914 na 1.16.29 permaneceram inalterados, assim como os 1.817 arquivos não DEX de cada APK.
- Reaplicação do patch rejeitada.
- Compilação conjunta testada com **Remove analytics** e **Fix home screen numbers tile** do d0nj v1.3.1. **Unlock Pro** não foi selecionado nesse teste.

Na 1.16.29 ainda falta validar widget, rede, permissões e cache em aparelho Android. Os testes locais não comprovam execução em ART. `tests/VerifyPatchedApk.java` permite conferir uma cópia original e uma cópia modificada fornecidas localmente; esses APKs não fazem parte do projeto.

## Código

- `patches/OpenMeteoWeatherPatch.kt`: valida o APK alvo e injeta o hook específico do builds 1634 e 1635.
- `extension/OpenMeteoProvider.java`: transporte HTTPS e conversão Open-Meteo → contrato JSON do Niagara.
- `extension/NiagaraWeatherBridge.java`: ponte para parser e cache originais.
- `tests/`: verificações e fixture.
- `docs/design.md`: arquitetura e limites.

GPLv3; veja [LICENSE](LICENSE) e [NOTICE.md](NOTICE.md). Projeto não oficial, baseado na estrutura de patches do d0nj. Não afiliado ao Niagara, Morphe ou Open-Meteo.


