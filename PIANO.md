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

L'ordine non segue le schermate del web: mette davanti ciò che il nativo fa
meglio, e rimanda la parità di funzioni che la PWA già serve bene.

### Fase 0 — fatta

Scheletro che compila, si installa e si avvia. Due trappole di AGP 9
documentate nel primo commit.

### Fase 1 — aspetto: palette, barre di sistema, tipografia

Prima di ogni contenuto, perché da qui in avanti ogni schermata che guardo la
giudico sul nostro disegno e non sui valori di serie di Material.

- Palette portata dal web **con gli stessi valori esadecimali**: basalto per la
  struttura, rosso ATAC, verde del dato vivo, ambra degli avvisi. La regola del
  web vale identica: **la struttura è acromatica, il colore è un dato.**
- Tema chiaro e scuro, con la scala neutra ribaltata come in `globals.css`. E
  l'ambra e il rosso vanno ribaltati anche qui: sul web dimenticarlo ha reso
  illeggibili gli avvisi in modalità scura.
- Aspetto delle barre di sistema coerente col tema: adesso le icone sono bianche
  su fondo chiaro e non si leggono.
- Il carattere **Barlow** in fondo alla fase: richiede di impacchettare i file
  del font, ed è l'unica parte che non è configurazione.

### Fase 2 — la spina dorsale: arrivi a una fermata

È la schermata per cui esiste l'app, e contiene tutte le incognite tecniche in
un colpo: HTTP, JSON, stato, aggiornamento periodico, errori.

- `GET /api/stops/{id}/arrivals`
- Lista con distintivo linea, destinazione, attesa. Verde se tracciato in tempo
  reale, grigio se da tabella — la distinzione più importante dell'app.
- Aggiornamento ogni 15 secondi **solo a schermata visibile**, come fa
  `usePolling` sul web sospendendo in background.
- Errore: si mostrano gli ultimi dati validi con un avviso, non una schermata
  vuota. Un errore di rete non deve cancellare l'informazione che l'utente
  stava leggendo.

### Fase 3 — come ci si arriva: ricerca e preferiti

- `GET /api/stops/search` per la ricerca.
- Preferiti salvati in locale, illimitati, riordinabili. Come sul web: sono la
  cosa più utile al primo colpo perché non chiedono il permesso di posizione.
- Posizione solo su richiesta esplicita, per `GET /api/stops/nearby`.

### Fase 4 — il widget

La ragione per cui stiamo facendo l'app. Va progettato a parte perché ha
vincoli che le schermate non hanno.

- Mostra il prossimo passaggio a una fermata preferita, scelta configurando il
  widget.
- Si aggiorna con `WorkManager`, e **non** ogni quindici secondi: Android
  strozza gli aggiornamenti dei widget e il sistema può ignorarli. Va accettato
  che il dato sia vecchio di qualche minuto, e **scritto sul widget a che ora è
  aggiornato** — la stessa onestà della striscia `FeedStatus` sul web.
- Una sola richiesta HTTP per aggiornamento. Se serve, si aggiunge lato server
  un endpoint che restituisce esattamente ciò che il widget mostra.

### Fase 5 — notifiche (richiede lavoro sul server)

Rimandata per scelta: è l'unica fase che tocca il backend.

- Il worker oggi parla solo Web Push con VAPID. Servono un percorso FCM in
  `worker/push.ts`, le credenziali del progetto Firebase, e una colonna in
  `push_subscriptions` che distingua il tipo di destinatario.
- Le regole restano quelle del web, già tarate: finestra di 5 minuti, e la
  campanella non si offre sotto i 3 minuti perché la notifica arriverebbe
  troppo tardi.

### Fase 6 — parità: percorsi, ritardi, avvisi

Ultime perché la PWA le serve già bene e nessuna guadagna dal nativo. Da farsi
solo se l'app viene usata e la gente le chiede.

La **mappa** va valutata a parte: MapLibre ha l'SDK nativo e lo stile si
riusa, ma è la dipendenza più pesante di tutte. Non entra in una prima
versione.

---

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
