#!/usr/bin/env python3
"""
Genera icona del launcher e icona dello splash partendo da logo.png.

Si esegue a mano, non fa parte della build: gli asset generati sono
versionati. Rigenerarli a ogni compilazione avrebbe significato mettere
Python e Pillow fra i requisiti per compilare l'app.

    python3 grafica/genera-icone.py

PERCHE' NON L'ILLUSTRAZIONE INTERA. Il logo e' una scena larga: bus,
Colosseo, pini, sole, strada. Sullo splash si vede a 192dp e funziona.
Sul launcher si vede a 48dp, e a quella dimensione alberi e archi
diventano poltiglia. Provate tutte le varianti su un provino, l'unica
leggibile a 48dp e' il muso del bus da solo: e' una forma sola, con una
sagoma riconoscibile e il contrasto crema-su-rosso.

PERCHE' IL FONDO PIENO E NON UN DISCO. La versione con un disco rosso
dentro un fondo navy era la piu' bella sul cerchio, ma la maschera del
launcher la decide il telefono: su un quadrato con angoli arrotondati
quell'anello navy diventa una cornice di spessore variabile, e sembra un
errore. Un fondo pieno e' corretto con qualunque maschera.
"""
from PIL import Image, ImageDraw
import numpy as np
import pathlib

RADICE = pathlib.Path(__file__).resolve().parent.parent
RES = RADICE / "app/src/main/res"
GRAFICA = RADICE / "grafica"

# Rosso ATAC: e' brand500 della palette chiara, lo stesso accento che l'app
# usa dentro. L'illustrazione ha un rosso piu' tenue (#D8624A, il sole), ma a
# 48dp perde contro la carrozzeria crema.
ROSSO = (0xC4, 0x16, 0x1C, 255)

# Densita' e fattore rispetto a mdpi.
DENSITA = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}


def riempi(consentito, semi):
    """Flood fill: dilatazione ristretta alla maschera fino a convergenza."""
    vis = np.zeros_like(consentito)
    for y, x in semi:
        vis[y, x] = consentito[y, x]
    for _ in range(4000):
        n = vis.copy()
        n[1:, :] |= vis[:-1, :]
        n[:-1, :] |= vis[1:, :]
        n[:, 1:] |= vis[:, :-1]
        n[:, :-1] |= vis[:, 1:]
        n &= consentito
        if n.sum() == vis.sum():
            return vis
        vis = n
    return vis


def estrai_bus(logo):
    """
    Isola il muso del bus dalla scena.

    Un ritaglio rettangolare si porta sempre dietro schegge di Colosseo e di
    strada, che a dimensione grande si vedono come artefatti sul bordo. Quindi
    si segmenta: la carrozzeria e' la regione crema connessa piu' grande, e i
    buchi interni (parabrezza, indicatore di destinazione, fari) appartengono
    al bus e si ritrovano riempiendo il complemento dal bordo dell'immagine.

    Restituisce (bus a colori, sagoma della sola carrozzeria). La seconda
    serve all'icona monocromatica: e' il bus con i vetri ritagliati, che come
    glifo si legge, mentre la sagoma piena sarebbe una macchia.
    """
    a = np.array(logo)
    r, g, b, al = (a[..., i].astype(int) for i in range(4))
    crema = (al > 200) & (r > 235) & (g > 215) & (b > 185) & (b < 240)

    # Seme dentro la carrozzeria, sopra l'indicatore di destinazione.
    carrozzeria = riempi(crema, [(425, 600)])
    if carrozzeria.sum() < 10_000:
        raise SystemExit("carrozzeria non trovata: il logo e' cambiato, "
                         "ricontrolla seme e soglie del crema")

    h, w = carrozzeria.shape
    bordo = ([(0, x) for x in range(0, w, 7)] + [(h - 1, x) for x in range(0, w, 7)]
             + [(y, 0) for y in range(0, h, 7)] + [(y, w - 1) for y in range(0, h, 7)])
    fuori = riempi(~carrozzeria, bordo)
    sagoma = carrozzeria | (~carrozzeria & ~fuori)

    colori = a.copy()
    colori[..., 3] = np.where(sagoma, colori[..., 3], 0)
    bus = Image.fromarray(colori)
    taglio = bus.getbbox()

    mono = np.zeros_like(a)
    mono[..., :3] = 255
    mono[..., 3] = np.where(carrozzeria, 255, 0)

    return bus.crop(taglio), Image.fromarray(mono).crop(taglio)


def su_tela(soggetto, lato, frazione, sfondo=None, disco=None):
    """Centra il soggetto su una tela quadrata, alto `frazione` del lato."""
    t = Image.new("RGBA", (lato, lato), sfondo or (0, 0, 0, 0))
    if disco:
        raggio, centro = lato * disco[0] / 2, lato / 2
        ImageDraw.Draw(t).ellipse(
            [centro - raggio, centro - raggio, centro + raggio, centro + raggio],
            fill=disco[1])
    h = max(1, round(lato * frazione))
    l = max(1, round(soggetto.width * h / soggetto.height))
    t.alpha_composite(soggetto.resize((l, h), Image.LANCZOS),
                      ((lato - l) // 2, (lato - h) // 2))
    return t


def scrivi(img, percorso):
    percorso.parent.mkdir(parents=True, exist_ok=True)
    img.save(percorso, optimize=True)
    print(f"  {percorso.relative_to(RADICE)}  {img.size[0]}x{img.size[1]}")


def main():
    logo = Image.open(RADICE / "logo.png").convert("RGBA")
    bus, mono = estrai_bus(logo)
    scrivi(bus, GRAFICA / "bus.png")

    # ---- ICONA DEL LAUNCHER (adaptive icon) ----------------------------
    # La tela di un livello e' 108dp, ma la maschera puo' mangiare tutto
    # fuori dai 72dp centrali e garantisce solo il cerchio da 66dp. Il bus
    # e' alto 0.58 del riquadro visibile, cioe' 0.58 * 72/108 della tela:
    # la sua diagonale resta dentro i 66dp anche con la maschera piu'
    # aggressiva.
    print("icona launcher:")
    for nome, f in DENSITA.items():
        lato = round(108 * f)
        scrivi(su_tela(bus, lato, 0.58 * 72 / 108),
               RES / f"mipmap-{nome}/ic_launcher_foreground.png")
        scrivi(su_tela(mono, lato, 0.58 * 72 / 108),
               RES / f"mipmap-{nome}/ic_launcher_monochrome.png")

    # ---- ICONA DELLO SPLASH --------------------------------------------
    # Tela 288dp con il contenuto nei 192dp centrali: e' la geometria che
    # l'API dello splash si aspetta, e il cerchio rosso disegnato dentro il
    # PNG coincide con l'area che il sistema ritaglia. Disegnarlo qui invece
    # di usare windowSplashScreenIconBackgroundColor da' lo stesso risultato
    # su tutte le versioni di Android, incluse quelle servite dalla
    # libreria di compatibilita'.
    print("icona splash:")
    for nome, f in DENSITA.items():
        lato = round(288 * f)
        scrivi(su_tela(bus, lato, 0.58 * 192 / 288, disco=(192 / 288, ROSSO)),
               RES / f"drawable-{nome}/ic_splash.png")

    # ---- ICONA PER IL PLAY STORE ---------------------------------------
    # 512x512, senza trasparenza e senza maschera: la applica il negozio.
    print("play store:")
    scrivi(su_tela(bus, 512, 0.58 * 0.667, sfondo=ROSSO).convert("RGB"),
           GRAFICA / "icona-play-512.png")


if __name__ == "__main__":
    main()
