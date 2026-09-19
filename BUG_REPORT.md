# Controllo qualità — Bus Roma Android

Revisione statica dei sorgenti Kotlin/Compose (43 file, ~9.100 righe) alla
revisione `d577ba1`, branch di audit `audit/bug-hunter`. Nessun codice
applicativo è stato modificato da questa analisi: ogni correzione è affidata a
un agente su un worktree e un branch suoi.

Perimetro guardato: architettura Compose (stato, effetti, chiavi delle liste),
ViewModel, coroutine e annullamenti, ciclo di vita (`repeatOnLifecycle`,
`MapView`), rete e serializzazione, persistenza su DataStore, fusi orari.

---

## Bug affidati a un fixer

### 1. L'annullamento di una coroutine viene mostrato come errore di rete — CRITICO

`ricerca/RicercaViewModel.kt:63-90` e `percorsi/PercorsiViewModel.kt:104-135`

Entrambi i ViewModel annullano il lavoro precedente (`lavoro?.cancel()`) e poi
intercettano tutto con `catch (e: Exception)`. `CancellationException` È una
`Exception`: se il lavoro annullato era sospeso dentro il `try` — cioè nelle
chiamate di rete — il suo blocco `catch` viene eseguito e scrive lo stato
d'errore **sopra** lo stato della richiesta nuova, appena impostata a
"in corso".

Effetto visibile: digitando a ritmo umano (una lettera ogni ~400 ms, con
risposte da ~300 ms) compare "La ricerca non risponde." e i risultati si
svuotano mentre una ricerca valida è in volo; sul pianificatore, una seconda
ricerca lanciata a calcolo in corso fa comparire il guasto e spegne
"Calcolo…". Si sistema da sé all'arrivo della risposta, che è il modo peggiore
di sbagliare: sembra che l'app sia rotta a caso.

Nota secondaria segnalata al fixer: in `RicercaViewModel` le due `async` sono
figlie della `launch`, quindi il fallimento di una annulla la sorella e il
genitore anche con `await()` dentro il `try` (rimedio: `coroutineScope`).

- Branch: `fix/annullamento-ricerca` · fixer `fixer-annullamento-ricerca` (pane `w5:p1`)

### 2. `MappaPercorso` congela i dati della prima composizione — ALTO

`mappa/MappaPercorso.kt:158-246`

Il `LaunchedEffect(mapView)` gira una volta sola, perché `mapView` è un
`remember` stabile. Dentro restano catturati tre valori che invecchiano:

1. la lambda di `addOnMapClickListener` cerca la fermata toccata nella lista
   `fermate` della prima composizione. In `SchermataLinea`, cambiando verso
   dalla tendina, toccare un pallino non apre più la schedina; in
   `SchermataCorsa` la mappa compare anche con `mezzo != null` e fermate
   ancora vuote, e in quel caso il tocco resta morto per tutta la vita della
   schermata;
2. `lineColor`/`circleStrokeColor` usano il colore calcolato alla prima
   composizione: l'anagrafica della linea arriva da un'altra
   `LaunchedEffect`, quindi un colore che arriva dopo non viene mai applicato
   e il tracciato resta basalto;
3. `inquadrata` non viene mai riazzerato: al cambio di verso la geometria
   disegnata è un'altra ma la camera resta sul percorso precedente, che può
   stare fuori schermo.

- Branch: `fix/mappa-stato-fresco` · fixer `fixer-mappa-stato-fresco` (pane `w6:p1`)

### 3. Chiavi non univoche nella lista degli arrivi — MEDIO (potenziale crash)

`arrivi/SchermataArrivi.kt:180-200`

La chiave di `items` è `"routeId-directionId-etaTs"`, che non è garantita
univoca: il feed può portare due passaggi della stessa linea nello stesso
verso con lo stesso `eta_ts` (mezzi accodati, o due corse da tabella allo
stesso minuto), e `trip_id` è nullo sugli arrivi da tabella quindi non
disambigua. Con due chiavi uguali Compose solleva `IllegalArgumentException`
("Key was already used") e cade la schermata per cui esiste l'app. La stessa
stringa fa da chiave a `apertoId`, quindi due righe gemelle aprirebbero il
pannello avvisi insieme.

- Branch: `fix/chiavi-arrivi` · fixer `fixer-chiavi-arrivi` (pane `w7:p1`)

### 4. Il selettore della data parte dal giorno sbagliato dopo mezzanotte — MEDIO

`percorsi/QuandoParti.kt:112-113`

`rememberDatePickerState(initialSelectedDateMillis = inizio)` riceve un
istante qualunque della giornata romana, ma il DatePicker di Material 3 legge
quei millisecondi in UTC: fra mezzanotte e le due (ora legale) la data UTC è
ancora quella di ieri, quindi il selettore si apre preselezionando il giorno
precedente. Chi alle 00:30 pianifica per l'una di notte, se conferma senza
accorgersene, ottiene un piano calcolato ventiquattro ore prima. La lettura di
`selectedDateMillis` (righe 118-126) è invece corretta e va lasciata com'è.

È l'unica delle quattro correzioni interamente verificabile con un test
unitario: al fixer è chiesto di estrarre una funzione pura e di coprirla.

- Branch: `fix/giorno-selettore` · fixer `fixer-giorno-selettore` (pane `w8:p1`)

---

## Rilievi non affidati (da decidere, o da verificare lato server)

- **`GeometriaItinerario.percorsoDi` chiama `Api.fermateLinea(tratta.shortName, verso)`**
  (`mappa/GeometriaItinerario.kt:196`) passando uno `short_name` dove tutti gli
  altri punti di chiamata passano un `route_id` — `TrattaInMezzo` non porta il
  `route_id`. Se `/api/routes/:id/stops` non accetta il nome breve, l'aggancio
  al tracciato **fallisce sempre in silenzio** (`catch` → `continue`) e ogni
  tratta in mezzo viene disegnata come retta. Va verificato sul server prima di
  toccare il client: se il server accetta entrambe le forme non c'è nulla da
  fare, altrimenti serve il `route_id` nella risposta di `/api/plan`.
- **Ciclo di vita della `MapView`** (`MappaPercorso.kt:120-156`,
  `MappaItinerario.kt:67-117`): la vista nasce con `onCreate/onStart/onResume`
  già chiamati e poi l'osservatore del ciclo di vita **ripete** `ON_START` e
  `ON_RESUME` alla registrazione; inoltre `onDispose` chiama `onDestroy()` su
  una `MapView` che il `remember` conserva, quindi un cambio di
  `LocalLifecycleOwner` la distruggerebbe restando in composizione. Oggi non si
  manifesta, ma è fragile.
- **Al cambio di verso i mezzi restano quelli del verso precedente** per un
  giro di polling (`linea/SchermataLinea.kt:137-155`): fino a 15 secondi di
  pallini verdi sulle fermate sbagliate. `mezzi` va svuotato al cambio di
  `verso`.
- **Il conto alla rovescia scatta a passi di 15 secondi**
  (`arrivi/ArriviViewModel.kt:245-259`): `adesso` si aggiorna solo a ogni
  richiesta, mentre il commento di `StatoArrivi.adesso` promette un
  aggiornamento "a ogni battito". O si aggiunge un battito da un secondo, o si
  corregge il commento.
- **`CampoCapo` non svuota i risultati quando un capo viene scelto**
  (`percorsi/CampoCapo.kt:79-105`): togliendo il capo con la crocetta ricompare
  subito l'elenco vecchio, perché `testo` non è cambiato.
- **`Tema.letturaIniziale` blocca il thread principale** con `runBlocking`
  (`impostazioni/Tema.kt:45-51`). È una scelta dichiarata e motivata (evitare
  il lampo di tema sbagliato al primo fotogramma); resta il rischio di ANR su
  un DataStore lento al primo avvio.
- **Nessuna infrastruttura di test**: non esistono `app/src/test`, né
  dipendenze `testImplementation`. `./gradlew testDebugUnitTest` passa a vuoto,
  quindi oggi non c'è nulla che trattenga una regressione sulla logica pura
  (`minutiDa`, `mezzanotteUtcDi`, aggancio dei tracciati, ordinamenti). I
  fixer di cui sopra hanno istruzioni per introdurre junit dove la correzione è
  testabile.
- `local.properties` non è nel repository (giustamente): ogni worktree nuovo
  parte senza `sdk.dir` e Gradle si ferma. Nei quattro worktree dei fixer è
  stato creato a mano.

## Come sono stati orchestrati i fixer

Un worktree per bug, tutti da `master`, un'istanza Claude (Sonnet) per
worktree:

| Bug | Branch | Fixer | Pane |
| --- | --- | --- | --- |
| Annullamento come errore | `fix/annullamento-ricerca` | `fixer-annullamento-ricerca` | `w5:p1` |
| Mappa con stato vecchio | `fix/mappa-stato-fresco` | `fixer-mappa-stato-fresco` | `w6:p1` |
| Chiavi arrivi duplicate | `fix/chiavi-arrivi` | `fixer-chiavi-arrivi` | `w7:p1` |
| Giorno iniziale del selettore | `fix/giorno-selettore` | `fixer-giorno-selettore` | `w8:p1` |

A ciascuno è chiesto di correggere solo il bug assegnato, di validare con
`./gradlew compileDebugKotlin` e `./gradlew testDebugUnitTest`, e di committare
sul proprio branch senza merge e senza push.
