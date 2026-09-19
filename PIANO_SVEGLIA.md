# Avvisami quando arriva — piano di implementazione

Notifica locale cinque minuti prima che una corsa scelta arrivi a una fermata,
con una campanella sulla riga dell'arrivo. È la controparte nativa di
`BellButton.tsx` + `worker/push.ts` di AtacWatch, con una differenza
sostanziale: **non c'è nessun push e nessuna modifica al server.**

## Perché senza push

Il server di AtacWatch parla Web Push standard (VAPID + RFC 8291) e non ha
niente di specifico per Firebase. Restavano tre strade: FCM, che su F-Droid
porta le anti-feature `NonFreeDep`/`NonFreeNet` e non funziona sui telefoni
senza servizi Google; UnifiedPush, che funziona ma obbliga ogni utente a
installare un distributore (ntfy); la sveglia locale, che non chiede niente a
nessuno.

Si sceglie la sveglia locale, perché il dato di cui abbiamo bisogno **il
telefono lo sa già chiedere**: l'ETA di una corsa a una fermata sta in
`/api/trips/:id`, che l'app interroga già per la schermata della corsa. Il
server fa il suo tick ogni 60 secondi; il telefono può fare di meglio, perché
vicino all'arrivo controlla ogni minuto e lontano non controlla quasi mai.

L'astrazione resta compatibile con UnifiedPush: se un giorno si vuole, si
aggiunge un secondo meccanismo dietro la stessa facciata `Avvisami`.

## Il ciclo

Una **vigilanza** è una riga: "avvisami quando `trip_id` arriva a `stop_id`" —
la stessa identità della tabella `push_subscriptions` del web.

1. Al tocco della campanella si conosce già l'`eta_ts` della riga. Si salva la
   vigilanza su DataStore e si programma il primo controllo.
2. A ogni **risveglio** (`AlarmManager` → `BroadcastReceiver`, telefono in
   tasca, schermo spento, processo riavviato se serve) si fa UNA richiesta:
   `Api.corsa(tripId)` e si legge l'`eta_ts` della fermata vigilata. Se la
   corsa non c'è — col feed ATAC fermo `/api/trips/:id` torna vuoto — si ricade
   su `Api.arrivi(stopId)` cercando lo stesso `tripId`.
3. Si decide: notificare, ricontrollare più tardi, o abbandonare.

### Le soglie, identiche al server

`worker/push.ts` notifica quando `arrival_ts` sta fra `now() - 1 minute` e
`now() + 5 minutes`. Qui vale lo stesso: si notifica quando il residuo è fra
−1 e +5,5 minuti. Il mezzo minuto in più copre lo scarto fra il risveglio e la
consegna della notifica, come il buffer che sul server copre il tick da 60s.

### La scala dei controlli

Una sveglia sola non basta, e la ragione è che un ETA può **anticipare**:
programmata a `eta − 6 min` sull'ETA visto al tocco, se il bus guadagna dieci
minuti quella sveglia suona a bus già passato. Quindi i controlli sono una
scala che si stringe avvicinandosi:

| residuo | prossimo controllo fra |
| --- | --- |
| > 30 min | 10 min |
| 15–30 min | 5 min |
| 8–15 min | 2 min |
| < 8 min | 60 s |

Regola: `prossimo = min(eta − 5 min, adesso + passo(residuo))`, mai meno di 30
secondi da adesso. Così l'errore sull'istante della notifica non supera un
passo della scala, e all'ultimo minuto il passo è un minuto. Una vigilanza da
40 minuti costa una dozzina di risvegli da pochi KB; una da 8 minuti ne costa
tre.

### Quando si abbandona

- residuo sotto −1 minuto: il bus è passato. Si chiude **in silenzio**, senza
  dire "è passato" a chi lo ha visto partire.
- oltre la **scadenza**: `eta iniziale + 30 minuti`, con un tetto assoluto di
  2 ore dalla creazione (è l'equivalente della pulizia a 2 ore del worker).
  Una corsa cancellata non deve tenere sveglio il telefono per sempre.
- controllo fallito (rete assente o corsa introvabile): i due casi arrivano a
  `decidi` indistinguibili. Tre tentativi a 60 secondi; se al terzo fallimento
  l'ETA noto è già dentro la finestra, si notifica **dichiarando che il dato
  non è fresco** — è lo stesso principio che la schermata degli arrivi applica
  già ai dati vecchi. Altrimenti si chiude in silenzio.

### La sveglia esatta e il suo permesso

Questa parte del piano era **sbagliata** e la correzione è arrivata dal
telefono: si era scritto che `setAlarmClock` non richiede permessi. Non è vero.
Da Android 12 sta nell'elenco delle API di sveglia esatta insieme a `setExact`
e `setExactAndAllowWhileIdle`, e senza permesso non degrada — solleva
`SecurityException`, cioè chiude l'app nel momento in cui si tocca la
campanella.

L'esattezza serve davvero: l'ultimo controllo è a un minuto dall'arrivo, e
`setAndAllowWhileIdle` — la sola inesatta che attraversa il Doze — non viene
consegnata più di una volta ogni 9 minuti, quindi la notifica dei cinque minuti
arriverebbe a bus passato. Quindi il permesso si dichiara, in due forme:
`USE_EXACT_ALARM` da Android 13, concesso all'installazione senza dialoghi, e
`SCHEDULE_EXACT_ALARM` con `maxSdkVersion="32"` per le versioni precedenti,
dove è pre-concesso. Il Play Store riserva `USE_EXACT_ALARM` alle app la cui
funzione principale sono sveglie e promemoria; la distribuzione qui è F-Droid,
dove quella politica non esiste, e se un giorno si pubblicasse su Play va
rivisto.

Il ripiego inesatto resta come rete di sicurezza, perché il permesso può essere
revocato da un'impostazione di sistema: in quel caso la notifica può arrivare
tardi, che è peggio che puntuale ma incomparabilmente meglio di un'app che si
chiude.

Fra le esatte resta `setAlarmClock`, la sola che il sistema non rimanda nemmeno
sotto restrizioni di batteria. Mostra l'icona della sveglia in barra di stato
mentre una vigilanza è pendente: è un effetto collaterale onesto, dice che
l'app sta aspettando qualcosa per te.

## Spartizione del lavoro

Il contratto (firme e KDoc, corpi `TODO()`) è già committato su questo branch:
ogni agente riempie i suoi file e non tocca quelli degli altri.

| Agente | File | Compito |
| --- | --- | --- |
| **nucleo** | `sveglie/Vigilanza.kt`, `sveglie/Cadenza.kt`, `app/src/test/.../CadenzaTest.kt` | Modello, deposito su DataStore, logica pura della scala e delle soglie, test unitari |
| **sveglie** | `sveglie/Sveglie.kt`, `sveglie/RicevitoreSveglia.kt`, `sveglie/RicevitoreAvvio.kt`, `sveglie/Avvisami.kt`, `AndroidManifest.xml` | `AlarmManager`, ricevitori, riarmo dopo il riavvio, facciata e orchestrazione del controllo |
| **notifica** | `sveglie/Notifiche.kt`, `MainActivity.kt`, `Navigazione.kt` | Canale, notifica, apertura sulla fermata al tocco |
| **interfaccia** | `ui/Glifi.kt`, `ui/Campanella.kt`, `arrivi/SchermataArrivi.kt` | Glifo, pulsante sulla riga, permesso notifiche al tocco, stato acceso/spento dal flusso |

La campanella compare **solo sulle righe con `tripId != null`**: è la stessa
condizione di `bellUtile` sul web, perché un arrivo da tabella non ha una corsa
da seguire.

## Fuori perimetro

Nessuna modifica al server, nessun endpoint nuovo, nessuna dipendenza nuova.
Niente UnifiedPush in questo passo. Niente servizio in primo piano: se un
giorno servisse più precisione nell'ultimo tratto, è un'aggiunta dietro la
stessa facciata.
