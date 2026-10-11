package com.example.offlinemediavault;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Перевод и группировка меток ML Kit. */
final class Labels {
    private Labels() {}

    private static final Map<String, String> RU = new HashMap<>();
    private static final Map<String, String> CAT = new HashMap<>();
    static final Set<String> SKIP = new HashSet<>();

    private static void r(String en, String ru) { RU.put(en.toLowerCase(Locale.ROOT), ru); }

    private static void cat(String en, String... names) { for (String n : names) CAT.put(n.toLowerCase(Locale.ROOT), en); }

    static {
        String[] skip = {"Fun", "Event", "Pattern", "Font", "Leisure", "Recreation", "Art", "Design", "Photography", "Material property", "Circle", "Rectangle", "Line",
                "Sky", "Tints and shades", "Electric blue", "Magenta", "Darkness", "Space", "Parallel", "Symmetry", "Visual arts", "Event", "Entertainment", "Technology", "Gadget", "Electronic device", "Room", "Interior design"};
        for (String s : skip) SKIP.add(s.toLowerCase(Locale.ROOT));
        String[] p = {
            "Dog|собака", "Cat|кошка", "Bird|птица", "Horse|лошадь", "Fish|рыба", "Insect|насекомое", "Butterfly|бабочка", "Flower|цветок", "Plant|растение", "Tree|дерево", "Grass|трава",
            "Mountain|горы", "Sea|море", "Beach|пляж", "Ocean|океан", "Lake|озеро", "River|река", "Water|вода", "Snow|снег", "Sunset|закат", "Sunrise|рассвет", "Cloud|облака", "Forest|лес",
            "Sky|небо", "Building|здание", "House|дом", "City|город", "Skyscraper|небоскрёб", "Bridge|мост", "Road|дорога", "Street|улица", "Car|авто", "Vehicle|транспорт", "Bicycle|велосипед",
            "Motorcycle|мотоцикл", "Airplane|самолёт", "Boat|лодка", "Ship|корабль", "Train|поезд", "Bus|автобус", "Food|еда", "Fruit|фрукты", "Vegetable|овощи", "Cake|торт", "Pizza|пицца",
            "Coffee|кофе", "Drink|напиток", "Dish|блюдо", "Meal|обед", "Dessert|десерт", "Person|люди", "Face|лицо", "Smile|улыбка", "Baby|малыш", "Child|ребёнок", "Crowd|толпа",
            "Selfie|селфи", "Wedding|свадьба", "Dance|танцы", "Concert|концерт", "Sport|спорт", "Football|футбол", "Basketball|баскетбол", "Swimming|плавание", "Ski|лыжи", "Snowboard|сноуборд",
            "Book|книга", "Text|текст", "Document|документ", "Paper|бумага", "Screenshot|скриншот", "Computer|компьютер", "Laptop|ноутбук", "Phone|телефон", "Mobile phone|телефон", "Television|телевизор",
            "Furniture|мебель", "Table|стол", "Chair|стул", "Bed|кровать", "Kitchen|кухня", "Bathroom|ванная", "Clothing|одежда", "Shoe|обувь", "Jewellery|украшения", "Toy|игрушка",
            "Music|музыка", "Guitar|гитара", "Painting|картина", "Sculpture|скульптура", "Museum|музей", "Church|церковь", "Temple|храм", "Castle|замок", "Monument|памятник", "Park|парк",
            "Garden|сад", "Farm|ферма", "Cow|корова", "Sheep|овца", "Pig|свинья", "Rabbit|кролик", "Bear|медведь", "Lion|лев", "Elephant|слон", "Monkey|обезьяна", "Deer|олень", "Duck|утка",
            "Night|ночь", "Fireworks|салют", "Christmas|рождество", "Christmas tree|ёлка", "Gift|подарок", "Party|вечеринка", "Birthday|день рождения", "Candle|свеча", "Fire|огонь",
            "Landscape|пейзаж", "Nature|природа", "Desert|пустыня", "Rock|камни", "Waterfall|водопад", "Island|остров", "Hill|холмы", "Field|поле", "Rain|дождь", "Fog|туман",
            "Tattoo|тату", "Hair|волосы", "Beard|борода", "Glasses|очки", "Hat|шляпа", "Bag|сумка", "Tool|инструмент", "Machine|машина", "Airport|аэропорт", "Hotel|отель", "Restaurant|ресторан",
            "Shopping|покупки", "Market|рынок", "Map|карта", "Poster|постер", "Cartoon|мультфильм", "Anime|аниме", "Game|игра", "Video game|видеоигра", "Pet|питомец", "Animal|животные"
        };
        for (String s : p) { int i = s.indexOf('|'); r(s.substring(0, i), s.substring(i + 1)); }
        cat("animals", "Dog", "Cat", "Bird", "Horse", "Fish", "Insect", "Butterfly", "Cow", "Sheep", "Pig", "Rabbit", "Bear", "Lion", "Elephant", "Monkey", "Deer", "Duck", "Pet");
        cat("nature", "Mountain", "Sea", "Beach", "Ocean", "Lake", "River", "Forest", "Tree", "Flower", "Plant", "Grass", "Sunset", "Sunrise", "Waterfall", "Island", "Desert", "Hill", "Field", "Landscape", "Snow");
        cat("food", "Fruit", "Vegetable", "Cake", "Pizza", "Coffee", "Drink", "Dish", "Meal", "Dessert", "Cuisine");
        cat("transport", "Car", "Vehicle", "Bicycle", "Motorcycle", "Airplane", "Boat", "Ship", "Train", "Bus");
        cat("city", "Building", "Skyscraper", "Bridge", "Road", "Street", "City", "House", "Church", "Castle", "Monument");
        cat("people", "Person", "Face", "Smile", "Baby", "Child", "Crowd", "Selfie", "Wedding", "Dance");
        cat("documents", "Text", "Document", "Paper", "Book", "Screenshot", "Map", "Poster");
    }

    private static final Map<String, String> CATRU = new HashMap<>();

    static {
        CATRU.put("animals", "животные"); CATRU.put("nature", "природа"); CATRU.put("food", "еда"); CATRU.put("transport", "транспорт");
        CATRU.put("city", "город"); CATRU.put("people", "люди"); CATRU.put("documents", "документы");
    }

    static String tr(String en, String lang) {
        String k = en.toLowerCase(Locale.ROOT);
        if ("ru".equals(lang)) {
            String v = RU.get(k);
            if (v != null) return v;
        }
        return k;
    }

    /** Название обобщающей категории для метки или null. */
    static String category(String en, String lang) {
        String c = CAT.get(en.toLowerCase(Locale.ROOT));
        if (c == null) return null;
        if ("ru".equals(lang)) return CATRU.get(c);
        return c;
    }
}
