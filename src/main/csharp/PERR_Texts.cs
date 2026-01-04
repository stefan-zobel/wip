namespace Misc;

internal class PERR_Texts
{
    //        ###
    //        ### SocketServer RCs:
    //        ### 
    //        RC001002.text.de=PSC Version ungueltig: '{0}' // VERY unlikely!
    //        RC001002.text.en=Invalid PSC version: '{0}'   // VERY unlikely!
    //        RC001003.text.de=PSC Nachrichtentyp ungueltig: '{0}'
    //        RC001003.text.en=Invalid PSC messagetype: '{0}'
    //        RC001004.text.de=PSC Code stimmt nicht mit PDO TYP ueberein. Gesendet: '{0}' Erwartet: '{1}' // VERY unlikely!
    //        RC001004.text.en=PSC code is not equal to PDO TYP: Sent: '{0}' Expected: '{1}'               // VERY unlikely!
    //        RC001007.text.de=Nutzdatenlaenge widerspricht Dateninhalt: '{0}'
    //        RC001007.text.en=Datalength contradicts content: '{0}'
    //        RC002001.text.de=Kein PDO erkannt: '{0}'
    //        RC002001.text.en=No PDO detected: '{0}'
    //        RC002005.text.de=Wert des Attributs '{1}' in Element '{0}' ungültig: '{2}' // comes from CheckAndPatchPdo
    //        RC002005.text.en=Value of attribute '{1}' in element '{0}' invalid: '{2}'  // comes from CheckAndPatchPdo
    //        RC999001.text.de=Interner Fehler: Telegramm konnte nicht verarbeitet werden. // is that EVER sent?
    //        RC999001.text.en=Internal error: Telegram could not be processed. // is that EVER sent?
    //        RC999002.text.de=Interner Fehler: Maschine {0} ist "verrückt".  // is that EVER sent?
    //        RC999002.text.en=Internal error: Machine {0} is bursted. // is that EVER sent?

    // Example PERR replies:

    //"PSCd2002006000108<PDO TYP=\"PERR\" VER=\"1\"><PERR CODE=\"2006\" TXT=\"Zugriff nicht erlaubt. 'Anlagenkennung' gefiltert.\" /></PDO>";
    //"PSCd2002006000103<PDO TYP=\"PERR\" VER=\"1\"><PERR CODE=\"2006\" TXT=\"Zugriff nicht erlaubt. 'IPAdresse' gefiltert.\" /></PDO>";
    //"PSCd2002006000102<PDO TYP=\"PERR\" VER=\"1\"><PERR CODE=\"2006\" TXT=\"Zugriff nicht erlaubt. 'Hostname' gefiltert.\" /></PDO>";
    //"PSCd2002006000106<PDO TYP=\"PERR\" VER=\"1\"><PERR CODE=\"2006\" TXT=\"Zugriff nicht erlaubt. 'Telegrammtyp' gefiltert.\" /></PDO>";
    //"PSCd2001008000114<PDO TYP=\"PERR\" VER=\"1\"><PERR CODE=\"1008\" TXT=\"Nachricht ist korrekt aber Maschine ist nicht angemeldet.\" /></PDO>";
    //"PSCd2001007000103<PDO TYP=\"PERR\" VER=\"1\"><PERR CODE=\"1007\" TXT=\"Nutzdatenlaenge widerspricht Dateninhalt: '25'\" /></PDO>";
    //"PSCd2001007000104<PDO TYP=\"PERR\" VER=\"1\"><PERR CODE=\"1007\" TXT=\"Nutzdatenlaenge widerspricht Dateninhalt: '229'\" /></PDO>";
    //"PSCd2999001000119<PDO TYP=\"PERR\" VER=\"1\"><PERR CODE=\"999001\" TXT=\"Interner Fehler: Telegramm konnte nicht verarbeitet werden.\" /></PDO>";
    //"PSCd2001004000105<PDO TYP=\"PERR\" VER=\"1\"><PERR CODE=\"1004\" TXT=\"PSC Code stimmt nicht mit PDO TYP ueberein: '1'\" /></PDO>"; // VERY unlikely!
    //"PSCd2002002000232<PDO TYP=\"PERR\" VER=\"1\"><PERR CODE=\"2002\" TXT=\"Ungueltiges Telegramm: '%3CPDO+TYP%3D%22PZTS%22+VER%3D%221%22+EZST%3D%221766502453%22%3E%0D%0A++%3CGMK+IP%3D%22127.0.0.1%22+KZB%3D%22Bohrer%22+ZST%3D%221218009670%22+MZ%3D%22'\" /></PDO>"


    // PKU example:
    // private static final KonfigUpdateInfo EMPTY_KONFIGUPDATE_INFO = new KonfigUpdateInfo("<nicht definiert>", "0");
    // private static final KonfigUpdateInfo EMPTY_KONFIGUPDATE_INFO = new KonfigUpdateInfo("<not defined>", "0");

}
