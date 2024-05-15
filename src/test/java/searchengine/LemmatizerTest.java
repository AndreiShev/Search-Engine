package searchengine;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import searchengine.lemmas.Lemmatizer;

import java.io.IOException;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
public class LemmatizerTest {
    static Lemmatizer lemmatizer;

    @BeforeAll
    private static void initialization() throws IOException {
        //lemmatizer = new Lemmatizer();

    }

    @ParameterizedTest
    @ValueSource(strings = {"Повторное появление леопарда в Осетии позволяет предположить, " +
            "что леопард постоянно обитает в некоторых районах Северного Кавказа."})
    public void getLemmasTest(String text) {
        try {
            HashMap<String, Integer> expected = new HashMap<>();
            expected.put("повторный", 1);
            expected.put("некоторый", 1);
            expected.put("появление", 1);
            expected.put("постоянно", 1);
            expected.put("позволять", 1);
            expected.put("предположить", 1);
            expected.put("северный", 1);
            expected.put("район", 1);
            expected.put("кавказ", 1);
            expected.put("осетия", 1);
            expected.put("леопард", 2);
            expected.put("обитать", 1);

            HashMap<String, Integer> actual = lemmatizer.getLemmas(text);

            if (text == null || text.isEmpty()) {
                assertEquals(new HashMap<>(), actual);
            } else {
                assertEquals(expected, actual);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
