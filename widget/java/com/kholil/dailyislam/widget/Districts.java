package com.kholil.dailyislam.widget;

/**
 * The 64 districts of Bangladesh, with the latitude/longitude of each
 * district town, used to calculate prayer times for the chosen district.
 */
public final class Districts {

    public static final class District {
        public final String nameBn;
        public final String nameEn;
        public final double latitude;
        public final double longitude;

        District(String nameBn, String nameEn, double latitude, double longitude) {
            this.nameBn = nameBn;
            this.nameEn = nameEn;
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }

    public static final District[] ALL = {
            new District("বাগেরহাট", "Bagerhat", 22.6516, 89.7859),
            new District("বান্দরবান", "Bandarban", 22.1953, 92.2184),
            new District("বরগুনা", "Barguna", 22.0953, 90.1121),
            new District("বরিশাল", "Barishal", 22.7010, 90.3535),
            new District("ভাঙ্গা, ফরিদপুর", "Bhanga, Faridpur", 23.3736, 90.0272),
            new District("ভোলা", "Bhola", 22.6859, 90.6482),
            new District("বগুড়া", "Bogura", 24.8510, 89.3711),
            new District("ব্রাহ্মণবাড়িয়া", "Brahmanbaria", 23.9571, 91.1116),
            new District("চাঁদপুর", "Chandpur", 23.2321, 90.6666),
            new District("চাঁপাইনবাবগঞ্জ", "Chapai Nawabganj", 24.5965, 88.2776),
            new District("চট্টগ্রাম", "Chattogram", 22.3569, 91.7832),
            new District("চুয়াডাঙ্গা", "Chuadanga", 23.6402, 88.8418),
            new District("কক্সবাজার", "Cox's Bazar", 21.4519, 91.9679),
            new District("কুমিল্লা", "Cumilla", 23.4683, 91.1788),
            new District("ঢাকা", "Dhaka", 23.8103, 90.4125),
            new District("দিনাজপুর", "Dinajpur", 25.6279, 88.6332),
            new District("ফরিদপুর", "Faridpur", 23.5426, 89.6308),
            new District("ফেনী", "Feni", 23.0159, 91.3976),
            new District("গাইবান্ধা", "Gaibandha", 25.3288, 89.5439),
            new District("গাজীপুর", "Gazipur", 23.9947, 90.4203),
            new District("গোপালগঞ্জ", "Gopalganj", 23.0051, 89.8266),
            new District("হবিগঞ্জ", "Habiganj", 24.3840, 91.4147),
            new District("জামালপুর", "Jamalpur", 24.9375, 89.9463),
            new District("যশোর", "Jashore", 23.1664, 89.2081),
            new District("ঝালকাঠি", "Jhalokati", 22.6406, 90.1987),
            new District("ঝিনাইদহ", "Jhenaidah", 23.5450, 89.1726),
            new District("জয়পুরহাট", "Joypurhat", 25.0968, 89.0227),
            new District("খাগড়াছড়ি", "Khagrachhari", 23.1193, 91.9847),
            new District("খুলনা", "Khulna", 22.8456, 89.5403),
            new District("কিশোরগঞ্জ", "Kishoreganj", 24.4260, 90.7829),
            new District("কুড়িগ্রাম", "Kurigram", 25.8072, 89.6295),
            new District("কুষ্টিয়া", "Kushtia", 23.8907, 89.1099),
            new District("লক্ষ্মীপুর", "Lakshmipur", 22.9444, 90.8286),
            new District("লালমনিরহাট", "Lalmonirhat", 25.9165, 89.4460),
            new District("মাদারীপুর", "Madaripur", 23.1644, 90.2056),
            new District("মাগুরা", "Magura", 23.4873, 89.4198),
            new District("মানিকগঞ্জ", "Manikganj", 23.8624, 89.9876),
            new District("মেহেরপুর", "Meherpur", 23.7625, 88.6314),
            new District("মৌলভীবাজার", "Moulvibazar", 24.4829, 91.7777),
            new District("মুন্সীগঞ্জ", "Munshiganj", 23.5422, 90.5305),
            new District("ময়মনসিংহ", "Mymensingh", 24.7471, 90.4203),
            new District("নওগাঁ", "Naogaon", 24.9132, 88.7531),
            new District("নড়াইল", "Narail", 23.1725, 89.5127),
            new District("নারায়ণগঞ্জ", "Narayanganj", 23.6238, 90.5000),
            new District("নরসিংদী", "Narsingdi", 23.9194, 90.7151),
            new District("নাটোর", "Natore", 24.4102, 89.0076),
            new District("নেত্রকোনা", "Netrokona", 24.8807, 90.7279),
            new District("নীলফামারী", "Nilphamari", 25.9318, 88.8560),
            new District("নোয়াখালী", "Noakhali", 22.8696, 91.0995),
            new District("পাবনা", "Pabna", 24.0064, 89.2372),
            new District("পঞ্চগড়", "Panchagarh", 26.3411, 88.5541),
            new District("পটুয়াখালী", "Patuakhali", 22.3596, 90.3298),
            new District("পিরোজপুর", "Pirojpur", 22.5796, 89.9759),
            new District("রাজবাড়ী", "Rajbari", 23.7574, 89.6445),
            new District("রাজশাহী", "Rajshahi", 24.3745, 88.6042),
            new District("রাঙ্গামাটি", "Rangamati", 22.6486, 92.1680),
            new District("রংপুর", "Rangpur", 25.7439, 89.2752),
            new District("সাতক্ষীরা", "Satkhira", 22.7086, 89.0718),
            new District("শরীয়তপুর", "Shariatpur", 23.2423, 90.3472),
            new District("শেরপুর", "Sherpur", 25.0205, 90.0153),
            new District("সিরাজগঞ্জ", "Sirajganj", 24.4534, 89.7007),
            new District("সুনামগঞ্জ", "Sunamganj", 25.0658, 91.3953),
            new District("সিলেট", "Sylhet", 24.9045, 91.8699),
            new District("টাঙ্গাইল", "Tangail", 24.2513, 89.9167),
            new District("ঠাকুরগাঁও", "Thakurgaon", 26.0337, 88.4617),
    };

    /** Display names for the district picker list, e.g. "ঢাকা (Dhaka)". */
    public static String[] displayNames() {
        String[] names = new String[ALL.length];
        for (int i = 0; i < ALL.length; i++) {
            names[i] = ALL[i].nameBn + " (" + ALL[i].nameEn + ")";
        }
        return names;
    }

    private Districts() {
    }
}
