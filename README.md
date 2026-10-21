# CloudStream spor eklentileri

İki CloudStream eklentisi:

- **SelcukSports** — selcuksportshd.is giriş sayfasından güncel site adresini bulur; Futbol, Basketbol, Tenis ve 7/24 TV listelerini gösterir.
- **NetSpor** — netspor.co'daki kanal ve maç listelerini (Futbol, Basketbol, Voleybol, Tenis) gösterir. Her yayın için sitedeki dört yedek sunucu ayrı kaynak olarak sunulur.

## Nasıl çalışır

| | Liste kaynağı | Yayın adresi |
|---|---|---|
| SelcukSports | Site ana sayfasındaki `a[data-url]` öğeleri | Oynatıcı sayfasındaki `baseStreamUrl` + `{id}/playlist.m3u8` |
| NetSpor | Ana sayfanın yüklediği script'teki `channels`, `futbolMatches` … dizileri | `event.html`'deki `baseurls` listesi + `{id}.m3u8` |

Alan adları ve yayın sunucuları her istekte sayfalardan okunur, kodda sabit değildir.

## Derleme

JDK 17 ve Android SDK gerekir (`local.properties` içinde `sdk.dir`).

```sh
./gradlew make makePluginsJson
```

Çıktılar: `SelcukSports/build/SelcukSports.cs3`, `NetSpor/build/NetSpor.cs3`, `build/plugins.json`.

## Kurulum

CloudStream → Ayarlar → Eklentiler → Depo ekle:

```
https://raw.githubusercontent.com/masteryodadev/cloudstream-spor/main/repo.json
```

**USB ile (geliştirme):** telefonda USB hata ayıklama açıkken

```sh
./gradlew SelcukSports:deployWithAdb NetSpor:deployWithAdb
```

**GitHub deposu ile:** bu dizini bir GitHub deposuna gönderin ve boş bir `builds` dalı oluşturun. `.github/workflows/build.yml` her push'ta `.cs3` dosyalarını ve `plugins.json`'u `builds` dalına yazar. Ardından CloudStream → Ayarlar → Eklentiler → Depo ekle kısmına `repo.json`'un raw adresini girin.
