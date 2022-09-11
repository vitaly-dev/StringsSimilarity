import info.debatty.java.stringsimilarity.Levenshtein;
import info.debatty.java.stringsimilarity.SorensenDice;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.apache.commons.lang3.StringUtils.isBlank;

/*          Здравствуйте!
 *      Как я вижу решение:
 *        Разбиваю на 2 листа строки. Сортировка листов бесполезна, тк одинаковые слова из 2-х строк листов в предложении могут
 * иметь разный порядок. Чтобы найти наибольшее сходство(similarity) между конкретными строками, нужно проитерироваться по
 * коллекциям без удаления их элементов, сравнивая коэффициент примененной функции Sorensen-Dice к 2-м строкам.
 * Sorensen-Dice коэффициент был выбран из-за его быстродействия: линейная алгоритмическая сложность алгоритма O(m+n).
 * При итерации 1-го листа наполняю withoutPairFirstList номерами строк без пары. Номера строк без пары из 2-го
 * листа можно определить после итерации циклов - withoutPairSecondList.
 *        Далее, чтобы подобрать пары из оставшихся "мало похожих/совсем непохожих" строк из листов withoutPairFirstList,
 * withoutPairSecondList можно применить функцию Levenshtein distance, чтобы найти наименьшее количество изменений над
 * 1-й строкой, необходимых для приведения ее к виду 2-й строки. При поиске пар для оставшихся строк в withoutPairFirstList,
 * withoutPairSecondList я не делал в цикле полный поиск как в первый раз, тк не посчитал это необходимым и делал
 * удаление найденной строки для пары из withoutPairSecondList.
 *
 *                                         Cost
 *      Levenshtein distance              O(m*n)  квадратичная сложность
 *      Sorensen-Dice coefficient         O(m+n)  линейная сложность
 *
 *      где m - длина 1-й строки, n - длина 2-й строки
 * */
public class StringsSimilarity {
    private static final Map<Integer, Map<Integer, Double>> resultMap = new LinkedHashMap<>();
    private static final String INPUT_PATH = System.getProperty("user.dir") + "/storage/input.txt";
    private static final String OUTPUT_PATH = System.getProperty("user.dir") + "/storage/output.txt";
    private static final Double LEVENSHTEIN_INITIAL = 1000.0;

    private static void createStringPairs() {
        Path inputFile = Paths.get(INPUT_PATH);
        Path outputFile = Paths.get(OUTPUT_PATH);

        try (BufferedReader bfr = Files.newBufferedReader(inputFile); BufferedWriter bfw = Files.newBufferedWriter(outputFile)) {
            int firstListSize = Integer.parseInt(bfr.readLine());
            List<String> firstList = bfr.lines().limit(firstListSize).toList();
            int secondListSize = Integer.parseInt(bfr.readLine());
            List<String> secondList = bfr.lines().limit(secondListSize).toList();
            List<Integer> withoutPairFirstList = new ArrayList<>();

            for (int i = 0; i < firstList.size(); i++) {
                String firstString = firstList.get(i).trim();
                if (isBlank(firstString)) {
                    continue;
                }
                Double similarity = 0.0;
                int indJ = -1;
                for (int j = 0; j < secondList.size(); j++) {
                    String secondString = secondList.get(j).trim();
                    if (isBlank(secondString)) {
                        continue;
                    }
                    SorensenDice sorensenDice = new SorensenDice();
                    double calcSimilarity = sorensenDice.similarity(firstString, secondString);
                    if (calcSimilarity > similarity) {
                        similarity = calcSimilarity;
                        indJ = j;
                    }
                }
                if (indJ >= 0) {
                    if (!resultMap.containsKey(indJ)) {
                        putInResultMap(i, similarity, indJ);
                    } else {
                        Map<Integer, Double> innerMap = resultMap.get(indJ);
                        Integer oldKey = innerMap.keySet().stream().findFirst().orElseThrow();
                        Double oldSimilarity = innerMap.get(oldKey);
                        if (similarity >= oldSimilarity) {
                            withoutPairFirstList.add(oldKey);
                            innerMap.remove(oldKey);
                            innerMap.put(i, similarity);
                        } else {
                            withoutPairFirstList.add(i);
                        }
                    }
                } else {
                    withoutPairFirstList.add(i);
                }
            }
            List<Integer> withoutPairSecondList = new ArrayList<>();
            AtomicInteger i = new AtomicInteger();
            secondList.forEach(string -> withoutPairSecondList.add(i.getAndIncrement()));
            withoutPairSecondList.removeAll(resultMap.keySet());
            int withoutPairDiffNum = withoutPairFirstList.size() - withoutPairSecondList.size();

            for (int y = 0; y < withoutPairFirstList.size() && !withoutPairSecondList.isEmpty(); y++) {
                Double distance = LEVENSHTEIN_INITIAL;
                int indZ = -1;
                for (int z = 0; z < withoutPairSecondList.size(); z++) {
                    Levenshtein dist = new Levenshtein();
                    double calcDistance = dist.distance(firstList.get(withoutPairFirstList.get(y)), secondList.get(withoutPairSecondList.get(z)));
                    if (calcDistance < distance) {
                        distance = calcDistance;
                        indZ = z;
                    }
                }
                putInResultMap(withoutPairFirstList.get(y), distance, withoutPairSecondList.get(indZ));
                withoutPairSecondList.remove(indZ);
            }
            // if withoutPairDiffNum = 0 - not need to put in to resultMap
            if (withoutPairDiffNum > 0) {
                for (int j = withoutPairFirstList.size() - withoutPairDiffNum; j < withoutPairFirstList.size(); j++) {
                    //  для 'key' вставляем в 'resultMap' произвольное число < 0 - для него не ищем строку в исходном листе строк
                    putInResultMap(withoutPairFirstList.get(j), null, -j - 1);
                }
            } else if (withoutPairDiffNum < 0) {
                for (int num : withoutPairSecondList) {
                    // вставляем в мапу 'key'=null - для него не ищем строку в исходном листе строк
                    putInResultMap(null, null, num);
                }
            }

            StringBuilder output = new StringBuilder();
            for (Map.Entry<Integer, Map<Integer, Double>> entry : resultMap.entrySet()) {
                List<Integer> integers = entry.getValue().keySet().stream().toList();
                String s1 = integers.get(0) == null ? null : firstList.get(integers.get(0));
                String s2 = entry.getKey() != null && entry.getKey() >= 0 ? secondList.get(entry.getKey()) : null;
                output.append(String.join("",
                        (s1 != null ? s1 : s2) + ": " + (s1 == null || s2 == null ? "?" : s2) + "\n"));
            }
            System.out.println(output);
            bfw.write(output.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void putInResultMap(Integer innerMapKey, Double innerMapValue, Integer resultMapKey) {
        Map<Integer, Double> innerMap = resultMap.computeIfAbsent(resultMapKey, key -> new HashMap<>());
        innerMap.put(innerMapKey, innerMapValue);
    }

    public static void main(String... args) {
        createStringPairs();
    }
}
