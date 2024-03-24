import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

public class DosRead {
    static final int FP = 1000;
    static final int BAUDS = 100;
    static final int[] START_SEQ = {1,0,1,0,1,0,1,0};
    FileInputStream fileInputStream;
    int sampleRate = 44100;
    int bitsPerSample;
    int dataSize;
    double[] audio;
    int[] outputBits;
    char[] decodedChars;

    /**
     * Constructor that opens the FIlEInputStream
     * and reads sampleRate, bitsPerSample and dataSize
     * from the header of the wav file
     * @param path the path of the wav file to read
     */
    public void readWavHeader(String path){
        // Création d'un tableau de bytes pour stocker l'en-tête WAV (44 bytes)
        byte[] header = new byte[44];

        try {
            // Ouverture d'un flux d'entrée pour lire le fichier WAV
            fileInputStream = new FileInputStream(path);
            // Lecture de l'en_tête pour obtenir les informations de celle-ci
            fileInputStream.read(header);
            // Extraire la fréquence d'échantillonnage du fichier WAV
            sampleRate = byteArrayToInt(header, 24, 32);
            // Extraire le nombre de bits par échantillon du fichier WAV
            bitsPerSample = byteArrayToInt(header, 34, 16);
            // Extraire la taille des données du fichier WAV
            dataSize = byteArrayToInt(header, 40, 32);

        } catch (FileNotFoundException e) {
            // Gérer l'exception si le fichier n'est pas trouvé
            e.printStackTrace();
        } catch (IOException e) {
            // Lancer une exception RuntimeException en cas d'erreur de lecture
            throw new RuntimeException(e);
        }
    }

    /**
     * Helper method to convert a little-endian byte array to an integer
     * @param bytes the byte array to convert
     * @param offset    the offset in the byte array
     * @param fmt   the format of the integer (16 or 32 bits)
     * @return  the integer value
     */
    private static int byteArrayToInt(byte[] bytes, int offset, int fmt) {
        // Si le format est 16 bits
        if (fmt == 16)
            // Construire l'entier en utilisant l'opération de décalage et l'opération OR
            return ((bytes[offset + 1] & 0xFF) << 8) | (bytes[offset] & 0xFF);
            // Si le format est 32 bits
        else if (fmt == 32)
            // Construire l'entier en utilisant les différentes opérations
            return ((bytes[offset + 3] & 0xFF) << 24) |
                    ((bytes[offset + 2] & 0xFF) << 16) |
                    ((bytes[offset + 1] & 0xFF) << 8) |
                    (bytes[offset] & 0xFF);
            // Si le format n'est ni 16 ni 32 bits, on retourne la valeur du premier byte
        else return (bytes[offset] & 0xFF);
    }

    /**
     * Read the audio data from the wav file
     * and convert it to an array of doubles
     * that becomes the audio attribute
     */
    public void readAudioDouble(){
        // Création d'un tableau de bytes pour stocker les données audio
        byte[] audioData = new byte[dataSize];
        try {
            // Lecture des données audio
            fileInputStream.read(audioData);
        } catch (IOException e) {
            // Gérer les exceptions liées à la lecture
            e.printStackTrace();
        }
        // Initialiser le tableau de valeurs doubles pour les données audio
        audio = new double[dataSize / (bitsPerSample / 8)];
        // Parcourir chaque valeur double dans le tableau
        for (int i = 0; i < audio.length; i++) {
            // Conversion des données audio en valeur double
            audio[i] = byteArrayToInt(audioData, i * (bitsPerSample / 8), bitsPerSample);
        }
    }

    /**
     * Reverse the negative values of the audio array
     */
    public void audioRectifier(){
        // Parcours de chaque valeurs dans le tableau audio
        for (int i = 0; i < audio.length; i++) {
            // Si la valeur est négative, prendre la valeur absolue
            // La valeur devient positive
            if (audio[i] < 0) {
                audio[i] = Math.abs(audio[i]);
            }
        }
    }


    /**
     * Apply a low pass filter to the audio array
     * Fc = (1/2n)*FECH
     * @param n the number of samples to average
     */
    public void audioLPFilter(int n) {
        // Variables pour stocker les valeurs maximales de gain et leur fréquence de coupure
        double gainMaximum = 0;
        double frequenceCoupure = 0;
        // Parcours de chaque valeur dans le tableau audio
        for (int i = 0; i < audio.length; i++) {
            // Calcul de l'amplitude de la sinusoïde en sortie du filtre
            // Calcul de l'amplitude de la sinusoïde en entrée du filtre
            double sortieFiltre = audio[i];
            double entreeFiltre = Math.abs(audio[i]);
            // Calcul du gain actuel en décibels
            double gain = 20 * Math.log10(sortieFiltre / entreeFiltre);
            // Mis a jour du gain maximal et de sa fréquence de coupure associée
            if (gain > gainMaximum) {
                gainMaximum = gain;
            } else if (gainMaximum - gain <= 3) {
                frequenceCoupure = i;
            }
        }
        // Appliquer le filtre passe-bas à partir de la fréquence de coupure
        // Calculer la somme des valeurs
        for (int i = n; i < audio.length; i++) {
            double sumEchantillon = 0;
            for (int j = i - n; j <= i; j++) {
                sumEchantillon += audio[j];
            }
            // Calculer la moyenne des échantillons dans la fenêtre spécifiée
            double moyenne = sumEchantillon / (n + 1);
            // Si l'échantillon d'indice i est supérieure à la fréquence de coupure
            // On applique la moyenne
            if (audio[i] >= frequenceCoupure) {
                audio[i] = moyenne;
            }
        }
    }


    /**
     * Resample the audio array and apply a threshold
     * @param period the number of audio samples by symbol
     * @param threshold the threshold that separates 0 and 1
     */
    public void audioResampleAndThreshold(int period, int threshold){
        // Calcul de la taille du tableau après rééchantillonnage
        int reechantillonage = audio.length / period;
        // Calcul de la taille ajustée pour être divisible par 8
        int tailleAjustee = ((reechantillonage + 7) / 8) * 8;
        // Initialisation du tableau de bits après le rééchantillonnage
        outputBits = new int[tailleAjustee];
        // Parcours de chaque échantillon après le rééchantillonnage
        for (int i = 0; i < reechantillonage; i++) {
            double somme = 0;
            // Calcul de la moyenne des échantillons dans la période spécifiée
            for (int j = 0; j < period; j++) {
                somme += audio[i * period + j];
            }
            double moyenne = somme / period;
            // Appliquer le seuil et assigner 1 ou 0 en fonction de la moyenne
            if (moyenne >= threshold) {
                outputBits[i] = 1;  // Si seuil inférieur a la valeur, assigner 1
            } else {
                outputBits[i] = 0;  // Valeur en dessous du seuil, assigner 0
            }
        }
        // Remplir les bits manquants avec des zéros
        for (int i = reechantillonage; i < tailleAjustee; i++) {
            outputBits[i] = 0;
        }
    }


    /**
     * Decode the outputBits array to a char array
     * The decoding is done by comparing the START_SEQ with the actual beginning of outputBits.
     * The next first symbol is the first bit of the first char.
     */
    public void decodeBitsToChar() {
        // Vérification de la correspondance de la séquence de démarrage
        for (int i = 0; i < START_SEQ.length; i++) {
            if (outputBits[i] != START_SEQ[i]) {
                System.out.println("La séquence de démarrage START_SEQ ne correspond pas avec la séquence renvoyé");
            }
        }
        // Calcul du nombre d'octets pour connaitre le nombre de caractère dans la chaîne
        int longueurChaine = (outputBits.length - START_SEQ.length) / 8;
        decodedChars = new char[longueurChaine];
        // Parcours de chaque caractère à décoder
        for (int i = 0; i < longueurChaine; i++) {
            int charValue = 0;
            // Parcours de chaque bit du caractère
            for (int j = 0; j < 8; j++) {
                // Construction de la valeur du caractère
                // Inversement de l'ordre des bits de l'octet
                charValue = charValue * 2 + outputBits[START_SEQ.length + i * 8 + (7 - j)];
            }
            // Conversion de charValue en caractère
            decodedChars[i] = (char) charValue;
        }
    }


    /**
     * Print the elements of an array
     * @param data the array to print
     */
    public static void printIntArray(char[] data) {
        // Parcours du tableau d'entiers et imprime chaque élément suivi d'un espace
        for (int i = 0; i < data.length; i++) {
            System.out.print(data[i] + " ");
        }
        // Aller à la ligne après l'impression du tableau
        System.out.println();
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
        // Affiche des lignes verticales et les valeurs correspondantes le long de la ligne horizontale
        for (int i = start; i < stop; i += 5000) {
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

    /**
     *  Un exemple de main qui doit pourvoir être exécuté avec les méthodes
     * que vous aurez conçues.
     */
    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: java DosRead <input_wav_file>");
            return;
        }
        String wavFilePath = args[0];
        // Open the WAV file and read its header
        DosRead dosRead = new DosRead();
        dosRead.readWavHeader(wavFilePath);
        // Print the audio data properties
        System.out.println("Fichier audio: " + wavFilePath);
        System.out.println("\tSample Rate: " + dosRead.sampleRate + " Hz");
        System.out.println("\tBits per Sample: " + dosRead.bitsPerSample + " bits");
        System.out.println("\tData Size: " + dosRead.dataSize + " bytes");
        // Read the audio data
        dosRead.readAudioDouble();
        // reverse the negative values
        dosRead.audioRectifier();
        // apply a low pass filter
        dosRead.audioLPFilter(44);
        // Resample audio data and apply a threshold to output only 0 & 1
        dosRead.audioResampleAndThreshold(dosRead.sampleRate/BAUDS, 12000 );
        dosRead.decodeBitsToChar();
        if (dosRead.decodedChars != null){
            System.out.print("Message décodé : ");
            printIntArray(dosRead.decodedChars);
        }
        displaySig(dosRead.audio, 0, dosRead.audio.length-1, "line", "Signal audio");
        // Close the file input stream
        try {
            dosRead.fileInputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
