package com.data.mining

import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.analysis.en.EnglishAnalyzer
import org.apache.lucene.document.*
import org.apache.lucene.index.*
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser
import org.apache.lucene.search.*
import org.apache.lucene.store.Directory
import org.apache.lucene.store.FSDirectory
import org.apache.lucene.util.ResourceLoader
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path
import java.util.*
import java.util.regex.Matcher
import java.util.regex.Pattern

val apiKey = "your_api_key"

val indexPath = "/Master/Sem1/Data Mining/Data_Mining_Project/src/main/resources/tmp"
val wikiepdiaDirectory = "/Master/Sem1/Data Mining/Data_Mining_Project/src/main/resources/wiki-subset-20140602"
val questionsFile = "/Master/Sem1/Data Mining/Data_Mining_Project/src/main/resources/questions.txt"

data class WikiPage(
    var title: String = "",
    var categories: MutableList<String> = mutableListOf(),
    var body: String = ""
)

data class PageParseResult(
    val pageSet: MutableSet<WikiPage>,
    val redirectPageTitles: MutableList<Pair<String, String>>
)

fun printMenu() {
    println("==========")
    println("1. Create index")
    println("2. Run query")
    println("3. Run questions")
    println("0. Exit")
    println("==========")
}

fun main() {
    var finished = false
    while(!finished) {
        printMenu()
        print("input: ")
        val input = readlnOrNull() ?: continue
        if (input == "0") {
            finished = true
        } else if (input == "1") {
            createIndex()
        } else if (input == "2") {
            runQuery()
        } else if (input == "3") {
            runQuestions()
        }
    }
}

fun runChatGPT(){
    val chatGPTUrl = "https://api.openai.com/v1/completions"  // Adjust based on your API endpoint

    // Search query and list of Wikipedia page answers
    val searchQuery = "1983 Beat It"
    val clue = "'80s NO.1 HITMAKERS"
    val answers = listOf("Page1", "Page2") // add the rest of the pages

    // Construct the prompt with the search query and answers
    val prompt = "I have this search $searchQuery and the clue $clue.Give back the best 10 wikipedia page titles for this search, but only the titles, nothing else"

    // Prepare the HTTP client
    val client = HttpClient.newHttpClient()

    val requestBody = """
        {
            "model": "gpt-3.5-turbo-1106",
            "messages": "[{role: \"User\", content \"How are you?\"]",
            "maxTokens": 100
        }
    """.trimIndent()

    // Prepare the request
    val request = HttpRequest.newBuilder()
        .uri(URI.create(chatGPTUrl))
        .header("Content-Type", "application/json")
        .header("Authorization", "Bearer $apiKey")
        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
        .build()

    try {
        // Send the request and get the response
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())

        // Parse and handle the response
        if (response.statusCode() == 200) {
            // Process the rearranged result from the response
            val rearrangedResult = response.body()
            println("Rearranged result: $rearrangedResult")
        } else {
            println("Error: ${response.statusCode()}")
            println(response.body())
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun runQuestions() {
    BufferedReader(FileReader(questionsFile)).use { reader ->
        var line: String?

        var listResults: List<Int> = listOf()

        val pattern = Regex("[.,:;!?-]")

        while (reader.readLine().also { line = it } != null) {
            if (line.isNullOrBlank()) {
                continue
            }
            val category = line!!.trim()
//                .replace("-", "\\-")
//                .replace("!", "\\!")
                .substringBefore("(")
                .replace(pattern, "")
            reader.readLine().also { line = it }
            val content = line!!.trim()
//                .replace("-", "\\-")
//                .replace("!", "\\!")
                .replace(pattern, "")
            reader.readLine().also { line = it }
            val expectedResult = line!!.trim()
            listResults += runSingleQuery(category, content, expectedResult)
        }

        println(listResults)
        val formattedMRR = String.format("%.3f", computeMRR(listResults))
        println("The MRR is : ${formattedMRR}")
        println("The correct number of top positions : ${listResults.filter { x-> x == 1 }.count()}/100")
    }
}

fun runSingleQuery(category: String, content: String, expectedResult: String): Int {
    try {
        val directory: Directory = FSDirectory.open(Path.of(indexPath))
        val reader = DirectoryReader.open(directory)

        var newCategory = category.substringBefore("(")

        var fields: Array<String> = arrayOf("content")

        var queryString = "($content) OR ($newCategory)"

        val parser = MultiFieldQueryParser(fields, getAnalyzer())

        val query: Query = parser.parse(queryString)

        val hitsPerPage = 30

        val searcher = IndexSearcher(reader)
        val docs = searcher.search(query, hitsPerPage)
        val hits = docs.scoreDocs
        println("Found " + hits.size + " hits.")

        var correctAnswers = expectedResult.split("|")
        var result: Int = 0

        for (i in hits.indices) {
            val docId = hits[i].doc
            val d = searcher.doc(docId)
            println((i + 1).toString() + ". " + "\t" + d["title"])
            if (correctAnswers.contains(d["title"]) && result == 0)
                result = i+1
        }

        println("content: $content")
        println("category: $category")
        println("expected result: $expectedResult\n")

        return result
    } catch (ex: RuntimeException) {
        println(ex.message)
        return 0
    }

}

fun runQuery() {
    val directory: Directory = FSDirectory.open(Path.of(indexPath))
    val reader = DirectoryReader.open(directory)

    val content = "In 2010: As Sherlock Holmes on film"
    val category = "GOLDEN GLOBE WINNERS"

    val fields = arrayOf("content", "category")

    val queryString = "${content} AND ${category}"

    val parser = MultiFieldQueryParser(fields, getAnalyzer())

    val query: Query = parser.parse(queryString)

    val hitsPerPage = 30

    val searcher = IndexSearcher(reader)
    val docs = searcher.search(query, hitsPerPage)
    val hits = docs.scoreDocs
    println("Found " + hits.size + " hits.")
    for (i in hits.indices) {
        val docId = hits[i].doc
        val d = searcher.doc(docId)
        println((i + 1).toString() + ". " + "\t" + d["title"])
    }
}

fun createIndex() {
    val directory = ResourceLoader::class.java.classLoader.getResource("wiki-subset-20140602")?.path
        ?: throw RuntimeException("Resource not found")

    val analyzer = getAnalyzer()
    val path = Path.of(indexPath)

    val index: Directory = FSDirectory.open(path)

    val config = IndexWriterConfig(analyzer)
    val indexWriter = IndexWriter(index, config)

    val files = getFilesFromDirectory(wikiepdiaDirectory)

    var pageSet: MutableSet<WikiPage> = mutableSetOf()
    var redirectPageTitles: MutableList<Pair<String, String>> = mutableListOf()

    for (file in files) {
        val result = processFile(file, pageSet, redirectPageTitles)
        pageSet = result.pageSet
        redirectPageTitles = result.redirectPageTitles
    }

    println("Finished parsing files")

    for (wikiPage in pageSet) {
        val currentDocument = Document()
        currentDocument.add(StringField("title", wikiPage.title, Field.Store.YES))

        val fieldType = FieldType(StringField.TYPE_STORED)
        fieldType.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS)
        if (wikiPage.categories.size == 0) {
            currentDocument.add(Field("category", "", fieldType))
        } else {
            for (category in wikiPage.categories) {
                currentDocument.add(Field("category", category, fieldType))
            }
        }

        currentDocument.add(TextField("content", wikiPage.body, Field.Store.NO))

        indexWriter.addDocument(currentDocument)
    }

    println("Saving index to file ...")

    indexWriter.commit()
    indexWriter.close()

    println("Finished all")

}

class CustomComparator : Comparator<WikiPage> {
    override fun compare(o1: WikiPage, o2: WikiPage): Int {
        return o1.title.compareTo(o2.title)
    }
}

fun processFile(
    filePath: String,
    pageSet: MutableSet<WikiPage>,
    redirectPageTitles: MutableList<Pair<String, String>>
): PageParseResult {
    val auxPageSet: MutableSet<WikiPage> = TreeSet(CustomComparator())
    val auxRedirectPageTitles: MutableList<Pair<String, String>> = mutableListOf()
    BufferedReader(FileReader(filePath)).use { reader ->
        var line: String?

        var pageBody = ""
        var page = WikiPage()
        var wasRedirect = false
        // Read lines from the file
        while (reader.readLine().also { line = it } != null) {
            if (line.isNullOrBlank()) {
                continue
            }
            if (isTitle(line)) {
                if (page.title.isNotBlank() && page.title.isNotEmpty() && !wasRedirect) {
                    page.body = pageBody
                    pageBody = ""
                    auxPageSet.add(page)
                }
                wasRedirect = false
                page = WikiPage()
                val title = retrieveTitle(line!!)
                // if it's a new title, create a new wiki page object
                page.title = title
            } else if (isCategoryLine(line)) {
                // line with categories
                page.categories = tokenizeCategoryLine(line!!)
            } else if (isRedirectLine(line)) {
                // line with redirect link
                auxRedirectPageTitles.add(Pair(page.title, getRedirectPageTitle(line!!)))
                wasRedirect = true
            } else {
                pageBody += line
            }
        }

        if (page.title.isNotBlank() && page.title.isEmpty()) {
            auxPageSet.add(page)
        }
    }
    pageSet.addAll(auxPageSet)
    redirectPageTitles.addAll(auxRedirectPageTitles)

    return PageParseResult(pageSet, redirectPageTitles)
}

fun getAnalyzer(): Analyzer {
    return EnglishAnalyzer()
}

fun computeMRR(ranks: List<Int>): Double {
    if (ranks.isEmpty()) {
        throw IllegalArgumentException("Input list of ranks is empty.")
    }

    return ranks.map { if (it == 0) 0.0 else 1.0 / it }.average()
}

fun isTitle(input: String?): Boolean {
    if (input == null) {
        return false
    }
    val regex = "^\\[\\[(.*?)]]$"
    val pattern: Pattern = Pattern.compile(regex)
    val matcher: Matcher = pattern.matcher(input)
    return matcher.matches()
}

fun retrieveTitle(input: String): String {
    val result = input.removePrefix("[[")
    return result.removeSuffix("]]")
}

fun isCategoryLine(input: String?): Boolean {
    if (input == null) {
        return false
    }
    return input.startsWith("CATEGORIES:")
}

fun tokenizeCategoryLine(input: String): MutableList<String> {
    return input.substring("CATEGORIES: ".length, input.length).split(", ").toMutableList()
}

fun isRedirectLine(input: String?): Boolean {
    if (input == null) {
        return false
    }
    return input.startsWith("#REDIRECT") || input.startsWith("#redirect")
}

fun getRedirectPageTitle(input: String): String {
    return input.substring("#REDIRECT ".length, input.length)
}

fun getFilesFromDirectory(directory: String): MutableList<String> {
    val directories: MutableList<String> = mutableListOf()
    val folder = File(directory)
    if (!folder.exists() && !folder.isDirectory) {
        throw RuntimeException("Folder does not exist")
    }

    val files = folder.listFiles() ?: throw RuntimeException("Files not found")
    for (file in files) {
        if (file.isFile) {
           directories.add(file.path)
        }
    }
    return directories
}