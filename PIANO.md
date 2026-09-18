# Bus Roma per Android — piano di implementazione

Client nativo sopra le API di [AtacWatch](https://bus.disagio.dev). Documento
di lavoro: le decisioni stanno qui con il loro perché, così quando una si
rivela sbagliata si sa cosa si stava cercando di ottenere.

---

## 1. Perché un'app nativa, e cosa NON compra

La PWA fa già installazione in home, notifiche push a schermo spento e shell
offline. Quindi il nativo si giustifica solo con quello che il web non può
fare:

| Cosa | Valore |
|---|---|
| **Widget in schermata home e blocco** | Il prossimo bus senza aprire niente. È la differenza più grande possibile per un'app che si apre di corsa alla fermata. |
| **Presenza sul Play Store** | La gente cerca lì, non sul web. |
| Notifiche più affidabili | FCM invece di Web Push. Marginale su Android, decisivo su iOS — che però non facciamo. |

**Non** lo facciamo per prestazioni o per aspetto: la PWA è già veloce e già
disegnata. Se a un certo punto ci accorgiamo di star riscrivendo schermate solo
per riaverle in Kotlin, quel lavoro va fermato.

### Vincoli deliberati

- **Solo Android.** iOS raddoppierebbe la manutenzione per un pubblico che non
  abbiamo. La PWA resta la risposta per iPhone.
- **Client stupido.** Zero logica di dominio a bordo: nessun calcolo di
  percorsi, nessuna aggregazione di statistiche, nessuna euristica sugli
  avvisi. Tutto arriva dalle API, che sono già modellate sulle schermate. Una
  correzione al pianificatore deve uscire con un `git push`, non con una
  release sullo store.
- **Nessun account.** Vale la stessa conclusione del web: l'identità non serve
  a niente di quello che facciamo, e una schermata di accesso è il punto di
  abbandono più grande di qualunque app.

---

## 2. Ordine dei lavori

**Deciso il 18/09/2026: prima la parità con il web, poi il widget.**

L'ordine iniziale metteva il widget al terzo posto, perché è l'unica cosa che
il web non può fare, e rimandava la parità. È stato invertito su richiesta, con
un argomento più forte: pubblicare sullo store qualcosa che fa meno del sito
già esistente significa che chi lo prova confronta e perde.

La conseguenza va scritta, perché è il rischio che ci prendiamo: ogni funzione
di parità raddoppia il costo di manutenzione senza aggiungere nulla che il web
non faccia, e la ragione per cui vale installare l'app arriva per ultima. Se a
metà strada l'app non è ancora usata da nessuno, quel lavoro è stato speso
male. Il segnale per fermarsi e ripensarci è questo: **se ci accorgiamo di
riscrivere una schermata solo per riaverla in Kotlin, quella schermata si
salta.**

### Fatto

| | |
|---|---|
| **0** | Scheletro su AGP 9.4 e Compose, due trappole documentate |
| **1** | Palette "palina", tipografia, Barlow con la variante condensata, barre di sistema |
| **2** | Arrivi a una fermata dalle API di produzione, aggiornamento ogni 15s, errori |
| **3a** | Ricerca fermate, navigazione tipizzata, indietro di sistema |
| **3b** | Preferiti persistenti su DataStore |

### Da fare, in ordine

**3c — fermate vicine.** Completa la schermata iniziale. Permesso di posizione
chiesto al tocco e mai all'avvio, come sul web. `GET /api/stops/nearby`.

**3d — riordino dei preferiti per trascinamento.** Sul web era un requisito
esplicito («voglio il drag vero da subito»). In Compose non c'è niente di
pronto: va fatto con `detectDragGesturesAfterLongPress` e gli scostamenti a
mano. `Preferiti.sposta()` esiste già.

**4 — completamento della schermata fermata.** Il contorno che sul web c'è e
qui no: gli avvisi di servizio sulla riga della linea coinvolta (col triangolo
non interattivo, come corretto sul web), lo stato del feed in cima, il tasto
del tema. `GET /api/alerts?stop=`, `GET /api/status`.

**5 — pagina linea e pagina corsa, senza mappa.** Versi, elenco fermate,
orari, mezzi in linea, avvisi della linea. Sono due schermate grosse ma senza
incognite: le API esistono e restituiscono esattamente ciò che serve.

**6 — la mappa.** MapLibre ha l'SDK nativo e lo stile di `mapStyle.ts` si
riusa, ma è la dipendenza più pesante di tutte e serve a tre schermate (linea,
corsa, e in futuro itinerario). Sta a sé perché è l'unico punto in cui il
lavoro non è una traduzione ma una reimplementazione.

**7 — pianificatore percorsi.** La schermata più complessa del web: due capi
con geocodifica, elenco di opzioni, dettaglio itinerario. Il client resta
stupido — il calcolo è tutto in `/api/plan` — ma l'interfaccia è articolata.
Va portata con la stessa onestà del web: dichiarare che gli orari sono da
tabella e senza tempo reale.

**8 — ritardi e avvisi.** Due schermate di sola lettura, le più semplici del
lotto. Le lascio tardi proprio per questo: non sbloccano niente.

**9 — notifiche (richiede lavoro sul server).** Rimandata per scelta
dell'utente. Il worker parla solo Web Push con VAPID: servono un percorso FCM
in `worker/push.ts`, le credenziali Firebase, e una colonna in
`push_subscriptions` per distinguere il tipo di destinatario. Le regole sono
già tarate sul web: finestra di 5 minuti, campanella nascosta sotto i 3.

**10 — il widget.** La ragione per cui l'app nativa esiste, e adesso arriva per
ultima. Vincoli già noti: Android strozza gli aggiornamenti, quindi va accettato
che il dato sia vecchio di qualche minuto e **scritto sul widget a che ora è
aggiornato** — la stessa onestà della striscia `FeedStatus`. Una sola richiesta
HTTP per aggiornamento, con `WorkManager`.

### Cosa NON portiamo

- **Il banner del caffè.** Su una pagina web ha senso, in un'app pubblicata
  sullo store le regole sulle donazioni sono un'altra faccenda e non vale
  aprirla adesso.
- **La schermata di benvenuto** al primo avvio, per ora: sul web serviva a
  spiegare cos'è l'app a chi arriva da un link. Chi installa dallo store ha
  già letto la descrizione.
- **Il service worker e la pagina privacy**: la prima non esiste come concetto,
  la seconda diventa un collegamento al sito, che è anche ciò che il Play Store
  richiede.

## 3. Decisioni tecniche

Prese adesso per non ridiscuterle a ogni schermata.

| Ambito | Scelta | Perché |
|---|---|---|
| HTTP | **Ktor client** (motore OkHttp) | Kotlin-first, coroutine native, e l'unica cosa che ci serve sono GET con timeout. Retrofit porterebbe un livello di annotazioni per niente. |
| JSON | **kotlinx.serialization** con `ignoreUnknownKeys = true` | Vedi il rischio in §4: l'app nel telefono di qualcuno deve sopravvivere a un campo aggiunto lato server. |
| Stato | **ViewModel + StateFlow** | Sopravvive alla rotazione e ai cambi di configurazione, che è l'unico motivo per cui esiste. |
| Iniezione dipendenze | **nessuna, per ora** | Con quattro schermate, costruire a mano è più leggibile di Hilt e non allunga la build. Si introduce quando fa male, non prima. |
| Navigazione | **Navigation Compose**, rotte tipizzate | Quattro o cinque destinazioni con argomenti: farlo a mano si rompe sul tasto indietro, che sul web ci è già costato due giri. |
| Preferiti | **DataStore** (Preferences) | È una lista di identificativi di fermata. Room sarebbe un database per un array. |
| Base URL | campo in `BuildConfig` | Così la build di debug può puntare a un server locale senza toccare il codice. |

---

## 4. Rischi, e cosa faremo al riguardo

**Le API non sono versionate.** Questo è il rischio serio: oggi le cambiamo
liberamente perché l'unico cliente è il web, che si aggiorna da sé. Con un'app
installata, ogni modifica incompatibile rompe i telefoni di chi non aggiorna.
Tre regole da qui in avanti: il client tollera campi sconosciuti; lato server
**non si rimuovono e non si rinominano campi**, si aggiungono; e se un giorno
serve una rottura, si versiona il percorso.

**Le API non hanno limiti di frequenza.** `/api/plan` esegue decine di
scansioni CSA per richiesta su un VPS da 4 GB condiviso col worker, che deve
battere ogni sessanta secondi. Prima di pubblicare l'app serve la regola di
rate limiting su Cloudflare (vedi `deploy/OPERATIONS.md` nel repo del web).

**L'app deve dichiararsi.** Uno `User-Agent` riconoscibile, così nei log e nei
limiti si distingue il traffico dell'app da quello del browser, e si può
tagliare un client impazzito senza spegnere il sito.

**Il nome del pacchetto è irreversibile.** `dev.disagio.busroma`, deciso ora:
cambiarlo dopo la pubblicazione significa un'app nuova e perdere gli installati.

**Per il Play Store** serviranno: informativa privacy raggiungibile — c'è, è
`/privacy` — il modulo sulla sicurezza dei dati, e un livello di API di
destinazione recente. L'informativa attuale dice «non c'è un account da
creare»: resta vera, e va tenuta vera.

---

## 5. Come si lavora

Il telefono è accoppiato via debug wireless, quindi l'anello completo si guida
da riga di comando:

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n dev.disagio.busroma/.MainActivity
adb exec-out screencap -p > /tmp/schermata.png
```

Vale la stessa disciplina del web: **si verifica guardando**, non si dà per
funzionante. In questa sessione guardare gli scatti ha fatto emergere la riga
degli arrivi troppo densa e l'ambra illeggibile in modalità scura, che nessuna
lettura del codice avrebbe rivelato.

Due inciampi già noti: se lo schermo è spento lo scatto esce nero — si controlla
con `dumpsys power | grep mWakefulness` — e se il telefono è bloccato si vede la
schermata di blocco. Conviene alzare il timeout di spegnimento nelle opzioni
sviluppatore.
