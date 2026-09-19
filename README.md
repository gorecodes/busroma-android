# Bus Roma

Gli autobus di Roma sul telefono: quanto manca davvero al prossimo passaggio,
dove sta il mezzo adesso, e come arrivare da qui a lì.

È un'app Android nativa in Kotlin e Compose, controparte del sito
[bus.disagio.dev](https://bus.disagio.dev). I dati vengono dal feed in tempo
reale di ATAC, letti da un server intermedio che li normalizza; l'app non ha
logica di dominio a bordo — le ragioni di questa e delle altre scelte stanno in
[PIANO.md](PIANO.md), che è il documento da leggere prima del codice.

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

1. si alzano `versionCode` e `versionName` in `app/build.gradle.kts`;
2. si spinge su `master`.

Il workflow [Rilascio](.github/workflows/rilascio.yml) vede che per quel
`versionName` non esiste ancora un tag, compila l'APK di rilascio, crea il tag
`v<versionName>` e pubblica la release con l'APK allegato. Le spinte che non
cambiano la versione non producono rilasci, quindi non serve ricordarsi di
niente. Ogni spinta e ogni richiesta di modifica passano comunque per
[Verifica](.github/workflows/verifica.yml), che esegue i test e la build di
debug.

L'APK della release esce **non firmato** finché non si configurano i quattro
secret della chiave — così com'è, il repository non contiene e non richiede
nessun segreto:

```sh
base64 -w0 ~/.busroma/busroma.jks | gh secret set FIRMA_KEYSTORE_BASE64
gh secret set FIRMA_STORE_PASSWORD
gh secret set FIRMA_KEY_ALIAS
gh secret set FIRMA_KEY_PASSWORD
gh secret set MAPTILER_KEY   # opzionale
```

Per F-Droid la firma non serve: lo store ricompila dal sorgente e firma con la
propria chiave. Vedi [FDROID.md](FDROID.md).

## Licenze

Il codice è sotto **licenza MIT** ([LICENSE](LICENSE)).

Non tutto il contenuto è nostro, e il resto conserva la sua licenza:

- il carattere **Barlow** è sotto SIL Open Font License, in
  [LICENZE/OFL-Barlow.txt](LICENZE/OFL-Barlow.txt);
- i dati delle mappe sono © contributori **OpenStreetMap**, sotto ODbL;
- il motore di mappa è **MapLibre GL Native**, BSD a due clausole;
- i dati di trasporto vengono dal feed pubblico di **ATAC / Roma Mobilità**.

Le attribuzioni sono anche dentro l'app, nella schermata Informazioni.
