# Bus Roma

Gli autobus di Roma sul telefono: quanto manca davvero al prossimo passaggio,
dove sta il mezzo adesso, e come arrivare da qui a lì.

È un'app Android nativa in Kotlin e Compose, controparte del sito
[bus.disagio.dev](https://bus.disagio.dev). I dati vengono dal feed in tempo
reale di ATAC, letti da un server intermedio che li normalizza; l'app non ha
logica di dominio a bordo — le ragioni di questa e delle altre scelte stanno in
[PIANO.md](PIANO.md), che è il documento da leggere prima del codice.

**Il sito e il server stanno in un altro repository:**
[gorecodes/AtacWatch](https://github.com/gorecodes/AtacWatch). Lì vivono gli
endpoint che questa app interroga, il lavoratore che digerisce il feed ATAC ogni
sessanta secondi e la versione web. Le due interfacce sono deliberatamente la
stessa cosa: dove una si comporta diversamente dall'altra, è un difetto di una
delle due.

Cosa sa fare:

- **arrivi a una fermata**, ricalcolati sull'orologio del telefono e non presi
  dall'istantanea del server, con il verde dei mezzi tracciati distinto dal
  grigio dell'orario previsto;
- **ricerca** di fermate e linee, con storico delle scelte e preferiti;
- **qui intorno**: cosa si può prendere adesso a partire dalla posizione;
- **percorso di una linea** con la mappa, le fermate in ordine e i mezzi che si
  muovono invece di saltare;
- **pianificatore** di itinerari con la mappa del viaggio;
- **statistiche di puntualità** per linea, contate sul feed;
- **avvisi di servizio**, marcati sulle righe delle linee che riguardano;
- **avvisami quando arriva**: notifica cinque minuti prima che la corsa scelta
  arrivi alla fermata, senza Firebase e senza push — la spiega
  [PIANO_SVEGLIA.md](PIANO_SVEGLIA.md).

Nessun tracciatore, nessuna pubblicità, nessuna dipendenza da Google Play: la
posizione usa il `LocationManager` della piattaforma e le mappe sono MapLibre
con dati OpenStreetMap.

## Compilare

Serve l'SDK Android; il resto lo scarica il wrapper di Gradle, compreso il JDK
25 che il progetto pretende per il demone.

```sh
echo "sdk.dir=$HOME/Android/Sdk" > local.properties
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

La piattaforma di compilazione è `android-37.2` — con la `37.0` le librerie
AndroidX si rifiutano di essere compilate — e il minimo supportato è Android 8
(API 26), perché a Roma girano ancora molti telefoni vecchi.

Le mappe vettoriali usano una chiave [MapTiler](https://www.maptiler.com/), che
è **opzionale**: senza, si ricade sulle tessere raster di OpenStreetMap come fa
il sito. Si passa dalla riga di comando e non finisce nel repository:

```sh
./gradlew assembleRelease -PmaptilerKey=xxxx
```

## Firmare

Le credenziali di firma stanno **fuori** dal repository, in
`~/.busroma/firma.properties` con permessi `600`:

```properties
storeFile=/home/tuonome/.busroma/busroma.jks
storePassword=...
keyAlias=busroma
keyPassword=...
```

Se il file non c'è, la build di rilascio esce **non firmata** invece di
fallire: chi clona il progetto deve poterlo compilare senza avere la chiave di
nessun altro. Una chiave committata per sbaglio non si revoca — chiunque
l'abbia può pubblicare aggiornamenti che i telefoni accettano come nostri.

## Pubblicare una versione

Il rilascio è automatico e la versione è la sola cosa da decidere a mano:

1. si alzano `versionCode` **e** `versionName` in `app/build.gradle.kts`;
2. si spinge su `master`.

Il workflow [Rilascio](.github/workflows/rilascio.yml) vede che per quel
`versionName` non esiste ancora un tag, compila l'APK di rilascio, crea il tag
`v<versionName>` e pubblica la release con **due** allegati: l'APK firmato e un
`ultima-versione.json`. Le spinte che non cambiano la versione non producono
rilasci, quindi non serve ricordarsi di niente. Ogni spinta e ogni richiesta di
modifica passano comunque per [Verifica](.github/workflows/verifica.yml), che
esegue i test e la build di debug.

### Le regole che impone l'aggiornatore interno

L'app installata controlla da sé se è uscita una versione nuova (il pacchetto
`aggiornamenti`) leggendo `releases/latest/download/ultima-versione.json`.
Violare una delle regole qui sotto **non rompe la build**: rompe
l'aggiornamento sui telefoni che hanno già l'app, che è un guasto silenzioso e
lo si scopre tardi.

- **`versionCode` si alza sempre, e solo in avanti.** È il numero su cui l'app
  decide se c'è qualcosa di nuovo: il nome della versione non lo guarda
  nessuno, perché "0.10.0" viene prima di "0.9.0" in qualunque ordinamento di
  stringhe.
- **Si alzano tutti e due.** Se alzi solo `versionCode`, il tag `v<versionName>`
  esiste già e il workflow non pubblica niente. Se alzi solo `versionName`, la
  release esce ma per i telefoni non è un aggiornamento, perché il
  `versionCode` è lo stesso di quello che hanno. Nessuno dei due casi dà un
  errore: danno silenzio.
- **La chiave di firma non cambia, mai.** Android rifiuta di installare un APK
  firmato con una chiave diversa da quella dell'app già installata. Cambiarla
  significa chiedere a ogni utente di disinstallare e reinstallare, perdendo
  preferiti, storico e vigilanze.
- **Gli allegati non si toccano a mano.** `ultima-versione.json` deve
  conservare quel nome esatto: l'indirizzo che l'app interroga è
  `releases/latest/download/ultima-versione.json`, e funziona proprio perché il
  nome è fisso mentre la versione cambia.

Una cosa invece la si può usare a proprio favore: `releases/latest` **esclude
le pre-release**. Marcando una release come pre-release l'APK resta scaricabile
da chi ha il link, ma i telefoni non la vedono come aggiornamento — è il modo
per provare una build su un telefono senza spedirla a tutti.

Il changelog è opzionale e sta in un posto solo: se esiste
`fastlane/metadata/android/it-IT/changelogs/<versionCode>.txt`, il workflow lo
usa come note della release **e** lo mette nel manifesto, quindi è anche il
testo che l'utente legge nel banner dell'aggiornamento.

Per una variante da dare a uno store che aggiorna da sé (F-Droid), l'aggiornatore
si spegne in build e non interroga nemmeno la rete:

```sh
./gradlew assembleRelease -PaggiornamentiInApp=false
```

### La firma in CI

È attiva. La chiave sta nell'ambiente GitHub `rilascio`, che è vincolato al
branch `master`: un workflow su un altro branch non riesce a leggerla. I quattro
secret sono `FIRMA_KEYSTORE_BASE64`, `FIRMA_STORE_PASSWORD`, `FIRMA_KEY_ALIAS`,
`FIRMA_KEY_PASSWORD`, e si rigenerano così:

```sh
base64 -w0 ~/.busroma/busroma.jks | gh secret set FIRMA_KEYSTORE_BASE64 --env rilascio
gh secret set FIRMA_STORE_PASSWORD --env rilascio
gh secret set FIRMA_KEY_ALIAS --env rilascio
gh secret set FIRMA_KEY_PASSWORD --env rilascio
gh secret set MAPTILER_KEY --env rilascio   # opzionale
```

Se i secret mancassero, l'APK esce **non firmato** invece di far fallire la
build: per uno store che ricompila dal sorgente e firma con la propria chiave
va bene così, ma su un telefono un APK non firmato non si installa.

## Licenze

Il codice è sotto **licenza MIT** ([LICENSE](LICENSE)).

Non tutto il contenuto è nostro, e il resto conserva la sua licenza:

- il carattere **Barlow** è sotto SIL Open Font License, in
  [LICENZE/OFL-Barlow.txt](LICENZE/OFL-Barlow.txt);
- i dati delle mappe sono © contributori **OpenStreetMap**, sotto ODbL;
- il motore di mappa è **MapLibre GL Native**, BSD a due clausole;
- i dati di trasporto vengono dal feed pubblico di **ATAC / Roma Mobilità**.

Le attribuzioni sono anche dentro l'app, nella schermata Informazioni.
