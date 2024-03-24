import java.io.File;
import java.io.FileOutputStream;
import java.util.List;
import java.util.Scanner;

public class DosSend {
    final int FECH = 44100; // fréquence d'échantillonnage
    final int FP = 1000;    // fréquence de la porteuses
    final int BAUDS = 100;  // débit en symboles par seconde
    final int FMT = 16;    // format des données
    final int MAX_AMP = (1 << (FMT - 1)) - 1; // amplitude max en entier
    final int CHANNELS = 1; // nombre de voies audio (1 = mono)
    final int[] START_SEQ = {1, 0, 1, 0, 1, 0, 1, 0}; // séquence de synchro au début
    static final Scanner input = new Scanner(System.in); // pour lire le fichier texte
    long taille;                // nombre d'octets de données à transmettre
    double duree;              // durée de l'audio
    double[] dataMod;           // données modulées
    char[] dataChar;            // données en char
    FileOutputStream outStream; // flux de sortie pour le fichier .wav


    /**
     * Constructor
     *
     * @param path the path of the wav file to create
     */
    public DosSend(String path) {
        // Création d'un fichier avec un chemin indiqué
        File file = new File(path);
        try {
            // Création d'un objet FileOutputStream associé au fichier pour créer un flux de sortie
            outStream = new FileOutputStream(file);
        } catch (Exception e) {
            // Si une exception est levé lors de la création de l'objet cela renvoie un message d'erreur
            System.out.println("Erreur de création du fichier");
        }
    }

    /**
     * Write a raw 4-byte integer in little endian
     *
     * @param octets     the integer to write
     * @param destStream the stream to write in
     */
    public void writeLittleEndian(int octets, int taille, FileOutputStream destStream) {
        // Déclaration d'une variable poidsFaible qui stocke l'octet de poids faible de l'entier
        char poidsFaible;
        // Tant que la taille est supérieur à 1 on extrait l'octet de point faible qui sera stocké dans la variable PoidsFaible
        while (taille > 0) {
            poidsFaible = (char) (octets & 0xFF);
            try {
                // crire l'octet de poids faible dans le flux de sortie.
                destStream.write(poidsFaible);
            } catch (Exception e) {
                // En cas d'exception cela renvoie un message d'erreur
                System.out.println("Erreur d'écriture");
            }
            // Décalage des bits de 8 positions vers la droite
            octets = octets >> 8;
            // Décrémention de la taille de 1 indiquant qu'un octet à été traité
            taille--;
        }
    }

    /**
     * Create and write the header of a wav file
     *
     */
    public void writeWavHeader() {
        // Calcul de la taille totale du fichier WAV en bytes
        taille = (long) (FECH * duree);
        long nbBytes = taille * CHANNELS * FMT / 8;

        try {
            // Écriture de la constante 'RIFF' pour indiquer le format du fichier
            outStream.write(new byte[]{'R', 'I', 'F', 'F'});
            // Écriture de la taille totale du fichier
            writeLittleEndian((int) (36 + nbBytes), 4, outStream);
            // Écriture du format 'WAVE' pour spécifier le type de format WAV
            outStream.write(new byte[]{'W', 'A', 'V', 'E'});

            // Écriture de l'identifiant' 'fmt ' pour définir le format des données audio
            outStream.write(new byte[]{'f', 'm', 't', ' '});
            // Écriture de la taille de la section 'fmt '
            writeLittleEndian(16, 4, outStream);
            // Écriture du format audio
            writeLittleEndian(1, 2, outStream);
            // Écriture du nombre de canaux
            writeLittleEndian(CHANNELS, 2, outStream);
            // Écriture de la fréquence d'échantillonnage
            writeLittleEndian(FECH, 4, outStream);
            // Écriture du nombre d'octet à lire par seconde
            writeLittleEndian(FECH * CHANNELS * FMT / 8, 4, outStream);
            // Écriture du nombre d'octets par bloc d'échantillonnage
            writeLittleEndian(CHANNELS * FMT / 8, 2, outStream);
            // Écriture du nombre de bits utilisés pour coder chaque échantillon
            writeLittleEndian(FMT, 2, outStream);

            // Écriture de la constante 'data' pour indiquer le début des données audio
            outStream.write(new byte[]{'d', 'a', 't', 'a'});
            // Écriture de la taille des données audio
            writeLittleEndian((int) nbBytes, 4, outStream);
        } catch (Exception e) {
            // En cas d'exception cela renvoie un message d'erreur
            System.out.println("Erreur de création d'entete");
        }
    }

    /**
     * Write the data in the wav file
     * after normalizing its amplitude to the maximum value of the format (8 bits signed)
     */
    public void writeNormalizeWavData() {
        try {
            // Parcours de chaque échantillon dans les données modulées
            for (int i = 0; i < dataMod.length; i++) {
                // Récupération l'échantillon actuel
                double echantillon = dataMod[i];
                // Déterminer l'amplitude en fonction de la parité de l'indice
                double amplitude;
                if (i % 2 == 0) {
                    amplitude = FP;
                } else {
                    amplitude = FP;
                }
                // Modulation de l'échantillon en générant une onde sinusoïdale
                echantillon *= Math.sin(2 * Math.PI * FP * i / FECH);
                // Appliquer l'amplitude à l'échantillon
                echantillon *= amplitude;
                // Normaliser l'échantillon en divisant par la fréquence de la porteuse
                echantillon /= FP;
                // Conversion de l'échantillon en entier avec une amplitude maximale
                int intEchantillon = (int) (echantillon * MAX_AMP);
                // Assigner l'échantillon modulé à dataMod
                dataMod[i] = intEchantillon;
                // Écriture de l'échantillon dans le flux de sortie
                writeLittleEndian(intEchantillon, 2, outStream);
            }
        } catch (Exception e) {
            // En cas d'exception cela renvoie une erreur
            System.out.println("Erreur d'écriture");
        }
    }

    /**
     * Read the text data to encode and store them into dataChar
     * @return the number of characters read
     */
    public int readTextData() {
        try {
            // Création d'un scanner pour lire l'entrée depuis la console
            Scanner input = new Scanner(System.in);
            // Utiliser un StringBuilder pour construire la chaîne de texte
            StringBuilder textData = new StringBuilder();
            // Lire toutes les lignes du fichier et les stocker dans le StringBuilder
            while (input.hasNextLine()) {
                textData.append(input.nextLine());
            }
            // Création d'un tableau de caractères pour stocker les données lues
            dataChar = new char[textData.length()];
            // Remplir le tableau de caractères avec les caractères de la chaîne de StringBuilder
            for (int i = 0; i < textData.length(); i++)
                dataChar[i] = textData.charAt(i);
            // Retourner la longueur du tableau de caractères
            return dataChar.length;
        } catch (Exception e) {
            // En cas d'exception cela retourne 0
            return 0;
        }
    }


    /**
     * convert a char array to a bit array
     * @param chars
     * @return byte array containing only 0 & 1
     */
    public byte[] charToBits(char[] chars) {
        // Création d'un tableau de bytes qui stocke la représention binaire
        byte[] bits = new byte[START_SEQ.length + 8 * chars.length];
        // Conversion des valeurs de START_SEQ en bytes
        for (int i = 0; i < START_SEQ.length; i++) {
            bits[i] = (byte) START_SEQ[i];
        }
        // Conversion des éléments de chars en entier (code ASCII)
        for (int i = 0; i < chars.length; i++) {
            int x = chars[i];
            // Calcul du reste de la division par 2
            for (int k = 0; k < 8; k++) {
                int y = x % 2;
                x /= 2;
                // Remplissage du tableau avec les bits obtenus
                bits[START_SEQ.length + 8 * i +k ] = (byte) y;
            }
        }

        return bits;
    }


    /**
     * Modulate the data to send and apply the symbol throughput via BAUDS and FECH.
     * @param bits the data to modulate
     */
    public void modulateData(byte[] bits) {
        // Calcul du débit de symbole en nombre d'échantillons par symbole
        int debitSymbole = FECH / BAUDS;
        // Initialisation le tableau pour stocker les données modulées
        dataMod = new double[bits.length * debitSymbole];
        // Parcourir chaque bit d'entrée
        for (int i = 0; i < bits.length; i++) {
            double amplitude;
            // Si le bit est 0, l'amplitude est 0, sinon elle est 1
            if (bits[i] == 0) {
                amplitude = 0;
            } else {
                amplitude = 1;
            }
            // Remplissage de dataMod avec l'amplitude pour chaque échantillon du symbole
            for (int j = 0; j < debitSymbole; j++) {
                dataMod[i * debitSymbole + j] = amplitude;
            }
        }
    }

    /**
     * Display a signal in a window
     * @param sig  the signal to display
     * @param start the first sample to display
     * @param stop the last sample to display
     * @param mode "line" or "point"
     * @param title the title of the window
     */
    public static void displaySig(double[] sig, int start, int stop, String mode, String title) {
        StdDraw.enableDoubleBuffering();
        StdDraw.setCanvasSize(1300, 750);
        StdDraw.setXscale(start, stop);
        StdDraw.setYscale(minimum(sig) - 5000, maximum(sig) + 5000);
        // Trace une ligne horizontale à y=0
        StdDraw.line(start, 0, stop, 0);
        StdDraw.text(start + 60, minimum(sig) - 2500, "-0.999");
        StdDraw.text(start + 60, maximum(sig) + 2500, "0.999");
        // Affiche des lignes verticales et les valeurs correspondantes le long de la ligne horizontale
        for (int i = start; i < stop; i += 200) {
            StdDraw.line(i, 0, i, -500);
            StdDraw.text(i, -2000, String.format("%.2f", (double) i));
        }
        // Dessine le signal en fonction du mode choisi ("line" ou "point")
        for (int i = start; i < stop - 1; i++) {
            StdDraw.setPenColor(StdDraw.BLUE);
            if (mode.equals("line")) {
                StdDraw.line(i, sig[i], i + 1, sig[i + 1]);
            } else if (mode.equals("point")) {
                for (i = start; i < stop; i++) {
                    StdDraw.point(i, sig[i]);
                }
            }
        }
        // Définit le titre de la fenêtre d'affichage
        StdDraw.setTitle(title);
        // Affiche le signal
        StdDraw.show();
    }


    /**
     * Display signals in a window
     * @param listOfSigs  a list of the signals to display
     * @param start the first sample to display
     * @param stop the last sample to display
     * @param mode "line" or "point"
     * @param title the title of the window
     */
    public static void displaySig(List<double[]> listOfSigs, int start, int stop, String mode, String title){
        StdDraw.enableDoubleBuffering();
        StdDraw.setCanvasSize(1300, 750);
        StdDraw.setXscale(start, stop);
        for(double[] sig : listOfSigs) {
            StdDraw.setYscale(minimum(sig) - 5000, maximum(sig) + 5000);
            StdDraw.line(start, 0, stop, 0);
            StdDraw.text(start + 60, minimum(sig) - 2500, "-0.999");
            StdDraw.text(start + 60, maximum(sig) + 2500, "0.999");
        }
        for (int i = start; i < stop; i += 200) {
            StdDraw.line(i, 0, i, -500);
            StdDraw.text(i, -2000, String.format("%.2f", (double) i));
        }
        for(double[] sig : listOfSigs) {
            for (int i = start; i < stop - 1; i++) {
                StdDraw.setPenColor(StdDraw.BLUE);
                if (mode.equals("line")) {
                    StdDraw.line(i, sig[i], i + 1, sig[i + 1]);
                } else if (mode.equals("point")) {
                    for (i = start; i < stop; i++) {
                        StdDraw.point(i, sig[i]);
                    }
                }
            }
        }
        StdDraw.setTitle(title);
        StdDraw.show();
    }

    public static double minimum(double[] tab){
        double min = tab[0];
        for (int i=0; i<tab.length; i++){
            if (tab[i]<min){
                min=tab[i];
            }
        }
        return min;
    }

    public static double maximum(double[] tab){
        double max = tab[0];
        for (int i=0; i<tab.length; i++){
            if (tab[i]>max){
                max=tab[i];
            }
        }
        return max;
    }

    public static void main(String[] args) {
        // créé un objet DosSend
        DosSend dosSend = new DosSend("DosOok_message.wav");
        // lit le texte à envoyer depuis l'entrée standard
        // et calcule la durée de l'audio correspondant
        dosSend.duree = (double)(dosSend.readTextData()+dosSend.START_SEQ.length/8)*8.0/dosSend.BAUDS;
        // génère le signal modulé après avoir converti les données en bits
        dosSend.modulateData(dosSend.charToBits(dosSend.dataChar));
        // écrit l'entête du fichier wav
        dosSend.writeWavHeader();
        // écrit les données audio dans le fichier wav
        dosSend.writeNormalizeWavData();
        // affiche les caractéristiques du signal dans la console
        System.out.println("Message : "+String.valueOf(dosSend.dataChar));
        System.out.println("\tNombre de symboles : "+dosSend.dataChar.length);
        System.out.println("\tNombre d'échantillons : "+dosSend.dataMod.length);
        System.out.println("\tDurée : "+dosSend.duree+" s");
        System.out.println();
        // exemple d'affichage du signal modulé dans une fenêtre graphique
        displaySig(dosSend.dataMod, 1000, 3000, "point", "Signal modulé");
    }
}
