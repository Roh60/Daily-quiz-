package com.example.data.local

object DatabasePrepopulator {

    fun getInitialBooks(): List<BookEntity> {
        return listOf(
            BookEntity(
                id = "book_lucent_gk",
                title = "Lucent's General Knowledge (English & Hindi)",
                publisher = "Lucent GK",
                edition = "Check publisher catalogue for current edition",
                description = "Resource directory entry for GK exam preparation. GK Swal does not include the full book text; open the publisher link to check current editions and availability.",
                licensingStatus = "Reference link only — no publisher endorsement claimed",
                officialUrl = "https://lucentpublication.com",
                totalChapters = 0,
                totalQuestions = 9,
                subjectsCovered = "Indian History, Geography, Indian Polity, General Science, Indian Economy, Static GK"
            ),
            BookEntity(
                id = "book_arihant_gk",
                title = "Arihant Publications — Study Resources",
                publisher = "Arihant Publications",
                edition = "Check publisher catalogue for current edition",
                description = "Resource directory entry for competitive-exam study materials. GK Swal does not include full copyrighted book text; open the publisher link to check current editions and availability.",
                licensingStatus = "Reference link only — no publisher endorsement claimed",
                officialUrl = "https://arihantbooks.com",
                totalChapters = 0,
                totalQuestions = 4,
                subjectsCovered = "Polity, History, Science, World Geography, Environmental Science, Quantitative GK"
            ),
            BookEntity(
                id = "book_drishti_ias",
                title = "Drishti IAS — Official Study Resources",
                publisher = "Drishti IAS",
                edition = "Check official website for current resources",
                description = "Resource directory entry for civil-services preparation. GK Swal does not include full copyrighted book or course text; open the official site to check current resources.",
                licensingStatus = "Reference link only — no affiliation claimed",
                officialUrl = "https://www.drishtiias.com",
                totalChapters = 0,
                totalQuestions = 4,
                subjectsCovered = "Indian Polity & Governance, Modern Indian History, Environment & Ecology, Economy, International Relations"
            )
        )
    }

    fun getInitialQuestions(): List<QuestionEntity> {
        return listOf(
            // --- LUCENT GK: POLITY ---
            QuestionEntity(
                id = "q_luc_pol_01",
                bookSource = "Lucent GK",
                subject = "Indian Polity",
                chapter = "Constituent Assembly & Preamble",
                examCategory = "SSC",
                question = "By which Constitutional Amendment Act were the words 'Socialist', 'Secular' and 'Integrity' added to the Preamble of the Indian Constitution?",
                optionA = "44th Amendment Act, 1978",
                optionB = "42nd Amendment Act, 1976",
                optionC = "52nd Amendment Act, 1985",
                optionD = "73rd Amendment Act, 1992",
                correctOptionIndex = 1,
                explanation = "The 42nd Constitutional Amendment Act of 1976 (known as the Mini-Constitution) added the three words: 'Socialist', 'Secular', and 'Integrity' to the Preamble under the Indira Gandhi government.",
                difficulty = "Easy",
                licensingStatus = "Original starter practice set — review pending"
            ),
            QuestionEntity(
                id = "q_luc_pol_02",
                bookSource = "Lucent GK",
                subject = "Indian Polity",
                chapter = "Fundamental Rights (Part III)",
                examCategory = "State PSC",
                question = "Which Article of the Constitution of India guarantees the 'Right to Constitutional Remedies' often termed by Dr. B.R. Ambedkar as the 'Heart and Soul' of the Constitution?",
                optionA = "Article 19",
                optionB = "Article 21",
                optionC = "Article 32",
                optionD = "Article 226",
                correctOptionIndex = 2,
                explanation = "Article 32 confers the right to move the Supreme Court for enforcement of fundamental rights via writs (Habeas Corpus, Mandamus, Prohibition, Quo-Warranto, Certiorari). Dr. Ambedkar described it as the very soul of the Constitution.",
                difficulty = "Easy",
                licensingStatus = "Original starter practice set — review pending"
            ),
            QuestionEntity(
                id = "q_luc_pol_03",
                bookSource = "Lucent GK",
                subject = "Indian Polity",
                chapter = "Union Judiciary",
                examCategory = "UPSC",
                question = "Under which Article can the President of India consult the Supreme Court on a question of law or fact of public importance?",
                optionA = "Article 129",
                optionB = "Article 136",
                optionC = "Article 143",
                optionD = "Article 148",
                correctOptionIndex = 2,
                explanation = "Article 143 authorizes the President to seek the advisory opinion of the Supreme Court. The opinion delivered by the SC is advisory in nature and not binding on the President.",
                difficulty = "Medium",
                licensingStatus = "Original starter practice set — review pending"
            ),

            // --- LUCENT GK: HISTORY ---
            QuestionEntity(
                id = "q_luc_hist_01",
                bookSource = "Lucent GK",
                subject = "History",
                chapter = "Indus Valley Civilization",
                examCategory = "SSC",
                question = "At which Harappan site was the famous dockyard and tidal port discovered?",
                optionA = "Kalibangan",
                optionB = "Lothal",
                optionC = "Banawali",
                optionD = "Mohenjo-daro",
                correctOptionIndex = 1,
                explanation = "Lothal in Gujarat (situated along the Bhogava river) was a major port town of the Harappan civilization featuring an engineered tidal dockyard for maritime trade.",
                difficulty = "Easy",
                licensingStatus = "Original starter practice set — review pending"
            ),
            QuestionEntity(
                id = "q_luc_hist_02",
                bookSource = "Lucent GK",
                subject = "History",
                chapter = "Modern Indian Freedom Movement",
                examCategory = "UPSC",
                question = "Who presided over the historic 1929 Lahore Session of the Indian National Congress where 'Purna Swaraj' (Complete Independence) was declared the ultimate goal?",
                optionA = "Mahatma Gandhi",
                optionB = "Subhas Chandra Bose",
                optionC = "Jawaharlal Nehru",
                optionD = "Sardar Vallabhbhai Patel",
                correctOptionIndex = 2,
                explanation = "Jawaharlal Nehru presided over the December 1929 Lahore Session where the resolution for Purna Swaraj was adopted, and 26 January 1930 was fixed as Independence Day.",
                difficulty = "Medium",
                licensingStatus = "Original starter practice set — review pending"
            ),
            QuestionEntity(
                id = "q_luc_hist_03",
                bookSource = "Lucent GK",
                subject = "History",
                chapter = "Medieval India - Delhi Sultanate",
                examCategory = "Railways",
                question = "Which Sultan introduced the 'Market Control Regulations' and fixed commodity prices in Delhi?",
                optionA = "Iltutmish",
                optionB = "Alauddin Khalji",
                optionC = "Muhammad bin Tughlaq",
                optionD = "Firoz Shah Tughlaq",
                correctOptionIndex = 1,
                explanation = "Alauddin Khalji instituted strict price controls and established dedicated markets (Shahna-i-Mandi) to maintain a massive standing army at low expenditure.",
                difficulty = "Easy",
                licensingStatus = "Original starter practice set — review pending"
            ),

            // --- LUCENT GK: GENERAL SCIENCE ---
            QuestionEntity(
                id = "q_luc_sci_01",
                bookSource = "Lucent GK",
                subject = "General Science",
                chapter = "Physics - Optics & Light",
                examCategory = "Railways",
                question = "What optical phenomenon explains the sparkling brilliance of a cut diamond and the occurrence of mirages in deserts?",
                optionA = "Total Internal Reflection",
                optionB = "Diffraction of light",
                optionC = "Polarization",
                optionD = "Atmospheric Scattering",
                correctOptionIndex = 0,
                explanation = "Diamonds have a very high refractive index (2.42) and a small critical angle (~24.4°). Light entering suffers multiple Total Internal Reflections, causing immense sparkle. Mirages are also caused by TIR in heated atmospheric air layers.",
                difficulty = "Easy",
                licensingStatus = "Original starter practice set — review pending"
            ),
            QuestionEntity(
                id = "q_luc_sci_02",
                bookSource = "Lucent GK",
                subject = "General Science",
                chapter = "Biology - Vitamins & Nutrition",
                examCategory = "SSC",
                question = "Deficiency of which fat-soluble vitamin causes delayed blood coagulation and hemorrhagic disease in newborns?",
                optionA = "Vitamin A",
                optionB = "Vitamin C",
                optionC = "Vitamin K",
                optionD = "Vitamin D",
                correctOptionIndex = 2,
                explanation = "Vitamin K (Phylloquinone) is an essential cofactor for the synthesis of prothrombin and factors VII, IX, and X in the liver, required for blood clotting.",
                difficulty = "Easy",
                licensingStatus = "Original starter practice set — review pending"
            ),
            QuestionEntity(
                id = "q_luc_sci_03",
                bookSource = "Lucent GK",
                subject = "General Science",
                chapter = "Chemistry - Periodic Properties",
                examCategory = "Defense",
                question = "Which noble gas is the most abundant by volume in Earth's dry atmosphere?",
                optionA = "Helium",
                optionB = "Argon",
                optionC = "Neon",
                optionD = "Krypton",
                correctOptionIndex = 1,
                explanation = "Argon accounts for approximately 0.934% of Earth's atmosphere by volume, making it the third most abundant gas after Nitrogen (~78%) and Oxygen (~21%).",
                difficulty = "Medium",
                licensingStatus = "Original starter practice set — review pending"
            ),

            // --- ARIHANT PUBLICATIONS: GEOGRAPHY & STATIC GK ---
            QuestionEntity(
                id = "q_ari_geo_01",
                bookSource = "Arihant Publications",
                subject = "Geography",
                chapter = "Physical Features of India",
                examCategory = "SSC",
                question = "Which channel or strait separates the Andaman Islands from the Nicobar Islands?",
                optionA = "8 Degree Channel",
                optionB = "9 Degree Channel",
                optionC = "10 Degree Channel",
                optionD = "Duncan Passage",
                correctOptionIndex = 2,
                explanation = "The 10 Degree Channel separates the Andaman Islands (Little Andaman) from the Nicobar Islands (Car Nicobar) in the Bay of Bengal along the 10° N parallel.",
                difficulty = "Easy",
                licensingStatus = "Original starter practice set — review pending"
            ),
            QuestionEntity(
                id = "q_ari_geo_02",
                bookSource = "Arihant Publications",
                subject = "Geography",
                chapter = "Indian River Systems",
                examCategory = "State PSC",
                question = "Which of the following peninsular rivers flows westwards into the Arabian Sea through a rift valley?",
                optionA = "Godavari",
                optionB = "Narmada",
                optionC = "Krishna",
                optionD = "Mahanadi",
                correctOptionIndex = 1,
                explanation = "The Narmada and Tapi rivers flow westwards through faulted rift valleys bounded by the Vindhya and Satpura mountain ranges, emptying into the Gulf of Khambhat.",
                difficulty = "Easy",
                licensingStatus = "Original starter practice set — review pending"
            ),
            QuestionEntity(
                id = "q_ari_static_01",
                bookSource = "Arihant Publications",
                subject = "Static GK",
                chapter = "Biosphere Reserves & National Parks",
                examCategory = "Banking",
                question = "In which Indian state is the Keibul Lamjao National Park, the world's only floating national park, located?",
                optionA = "Assam",
                optionB = "Manipur",
                optionC = "Meghalaya",
                optionD = "Arunachal Pradesh",
                correctOptionIndex = 1,
                explanation = "Keibul Lamjao National Park is situated on Loktak Lake in Bishnupur district, Manipur. It is famous for floating biomass called 'Phumdis' and is the last natural habitat of the endangered Sangai deer.",
                difficulty = "Medium",
                licensingStatus = "Original starter practice set — review pending"
            ),
            QuestionEntity(
                id = "q_ari_econ_01",
                bookSource = "Arihant Publications",
                subject = "Indian Economy",
                chapter = "Monetary Policy & Banking",
                examCategory = "Banking",
                question = "What is the term for the interest rate at which the Reserve Bank of India lends short-term liquidity to commercial banks against government securities?",
                optionA = "Reverse Repo Rate",
                optionB = "Repo Rate",
                optionC = "Bank Rate",
                optionD = "Marginal Standing Facility",
                correctOptionIndex = 1,
                explanation = "Repo Rate (Repurchasing Option rate) is the key benchmark policy rate at which RBI lends money to commercial banks against pledged collateral.",
                difficulty = "Easy",
                licensingStatus = "Original starter practice set — review pending"
            ),

            // --- DRISHTI IAS: ADVANCED POLITY, ENVIRONMENT & CIVIL SERVICES ---
            QuestionEntity(
                id = "q_dri_pol_01",
                bookSource = "Drishti IAS",
                subject = "Indian Polity",
                chapter = "Basic Structure Doctrine & Judicial Review",
                examCategory = "UPSC",
                question = "In which landmark verdict did the Supreme Court propound the doctrine of the 'Basic Structure' of the Indian Constitution, limiting Parliament's amending powers under Article 368?",
                optionA = "Golaknath v. State of Punjab (1967)",
                optionB = "Kesavananda Bharati v. State of Kerala (1973)",
                optionC = "Minerva Mills v. Union of India (1980)",
                optionD = "Maneka Gandhi v. Union of India (1978)",
                correctOptionIndex = 1,
                explanation = "In the 13-judge constitutional bench ruling of Kesavananda Bharati (April 24, 1973), the SC established that Parliament cannot alter the basic features or foundation of the Constitution.",
                difficulty = "Hard",
                licensingStatus = "Original starter practice set — review pending"
            ),
            QuestionEntity(
                id = "q_dri_pol_02",
                bookSource = "Drishti IAS",
                subject = "Indian Polity",
                chapter = "Federalism & Inter-State Council",
                examCategory = "UPSC",
                question = "Under Article 263 of the Constitution, who is empowered to establish the Inter-State Council to investigate and discuss subjects of common interest between States and the Union?",
                optionA = "The Parliament of India",
                optionB = "The President of India",
                optionC = "The Prime Minister of India",
                optionD = "The Chief Justice of India",
                correctOptionIndex = 1,
                explanation = "Article 263 provides that if at any time it appears to the President that the public interests would be served by the establishment of an Inter-State Council, the President may by order establish such a council.",
                difficulty = "Hard",
                licensingStatus = "Original starter practice set — review pending"
            ),
            QuestionEntity(
                id = "q_dri_env_01",
                bookSource = "Drishti IAS",
                subject = "Geography",
                chapter = "Environment & Climate Conventions",
                examCategory = "UPSC",
                question = "The 'Ramsar Convention' signed in 1971 is an international treaty primarily dedicated to the conservation and sustainable use of which ecological system?",
                optionA = "Tropical Rainforests",
                optionB = "Mangrove and Marine Corals",
                optionC = "Wetlands of International Importance",
                optionD = "Cold Desert Ecosystems",
                correctOptionIndex = 2,
                explanation = "The Ramsar Convention (signed in Ramsar, Iran in 1971) promotes the conservation and wise use of wetlands and their resources across the globe. India currently has 85+ designated Ramsar sites.",
                difficulty = "Medium",
                licensingStatus = "Original starter practice set — review pending"
            ),
            QuestionEntity(
                id = "q_dri_econ_02",
                bookSource = "Drishti IAS",
                subject = "Indian Economy",
                chapter = "External Sector & BOP",
                examCategory = "UPSC",
                question = "In the Balance of Payments (BoP) accounting of India, under which category are foreign remittances, software exports, and gifts classified?",
                optionA = "Capital Account - Foreign Direct Investment",
                optionB = "Current Account - Invisible Receipts",
                optionC = "Capital Account - External Commercial Borrowings",
                optionD = "Errors and Omissions",
                correctOptionIndex = 1,
                explanation = "Services exports (IT/BPO), private unilateral transfers (remittances from NRIs), and income from investment are recorded under Invisibles in the Current Account.",
                difficulty = "Hard",
                licensingStatus = "Original starter practice set — review pending"
            )
        )
    }

    // Local practice bank metadata for all 12 user-requested categories.
    fun getAdditionalTopicBook(): BookEntity = BookEntity(
        id = "book_gkswal_originals",
        title = "GK Swal Originals — 10,000 Quiz Bank",
        publisher = "GK Swal Originals",
        edition = "Starter edition",
        description = "7,997 database questions across 12 categories, plus 2,003 global currency GK MCQs loaded from assets, for 10,000 total quiz entries. Expansion includes alternate-wording revision variants; editorial review is pending and this is not represented as an official publisher question bank.",
        licensingStatus = "Original starter practice set — review pending",
        officialUrl = "https://play.google.com/store/apps/details?id=com.aistudio.gkswal.vqxrmp",
        totalChapters = 12,
        totalQuestions = 7997,
        subjectsCovered = "History, Indian Polity, Geography, General Science, Indian Economy, Static GK, Sports, Entertainment, Kids, Currency, Current Affairs, Facts"
    )

    fun getAdditionalTopicQuestions(): List<QuestionEntity> = ExpandedTopicQuestionBank.getQuestions() + FactsQuestionBank.getQuestions() + CategorySupplementQuestionBank.getQuestions() + ExpansionQuestionBank.getQuestions()

}
