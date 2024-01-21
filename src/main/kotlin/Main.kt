package com.data.mining

import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.analysis.en.EnglishAnalyzer
import org.apache.lucene.document.*
import org.apache.lucene.index.*
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser
import org.apache.lucene.search.*
import org.apache.lucene.store.Directory
import org.apache.lucene.store.FSDirectory
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

val OpenAI_APIKey = "your_api_key"

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
    val redirectPageTitles: MutableMap<String, MutableList<String>>
)

class CustomComparator : Comparator<WikiPage> {
    override fun compare(o1: WikiPage, o2: WikiPage): Int {
        return o1.title.compareTo(o2.title)
    }
}

fun printMenu() {
    println("==========")
    println("1. Create index")
    println("2. Run query")
    println("3. Run default questions")
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
            runUserQuery()
        } else if (input == "3") {
            runDefaultQuestions()
        }
    }
}

fun runChatGPT(){
    val chatGPTUrl = "https://api.openai.com/v1/completions"

    val clue = "In 2010 As Sherlock Holmes on film"
    val category = "GOLDEN GLOBE WINNERS"
    val answers = listOf("1. I. A. L. Diamond",
        "2. Sherlock Holmes, SherlockHolmes",
        "3. Hans Zimmer",
        "4. Irene Adler",
        "5. Edward Hardwicke",
        "6. Basil Rathbone",
        "7. Jeremy Brett",
        "8. The Five Orange Pips",
        "9. Feluda",
        "10. Robert Downey, Jr.")

    val message = "I am looking to find wikipedia pages for this clue: $clue and the category: $category. Also, there is a list with 10 wikipedia pages as possible answers: ${answers.joinToString()}. Return the 10 wikipedia page titles after you re-rank them for a higher chance of finding the clue in the wikipedia page, but only the titles, nothing else."

    val client = HttpClient.newHttpClient()

    val requestBody = """
        {
            "model": "gpt-3.5-turbo-1106",
            "messages": "[{role: \"User\", content \"$message\"]",
            "maxTokens": 100
        }
    """.trimIndent()

    val request = HttpRequest.newBuilder()
        .uri(URI.create(chatGPTUrl))
        .header("Content-Type", "application/json")
        .header("Authorization", "Bearer $OpenAI_APIKey")
        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
        .build()

    try {
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())

        if (response.statusCode() == 200) {
            val rearrangedResults = response.body()
            println("Rearranged result: $rearrangedResults")
        } else {
            println("Error: ${response.statusCode()}")
            println(response.body())
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun createIndex() {
    deleteFilesFromDirectory(indexPath)
    val analyzer = getAnalyzer()
    val path = Path.of(indexPath)

    val index: Directory = FSDirectory.open(path)

    val config = IndexWriterConfig(analyzer)
    val indexWriter = IndexWriter(index, config)

    val files = getFilesFromDirectory(wikiepdiaDirectory)

    var pageSet: MutableSet<WikiPage> = mutableSetOf()
    var redirectPageTitles: MutableMap<String, MutableList<String>> = mutableMapOf()

    for (file in files) {
        val result = processFile(file, pageSet, redirectPageTitles)
        pageSet = result.pageSet
        redirectPageTitles = result.redirectPageTitles
    }

    println("Finished parsing files")

    for (wikiPage in pageSet) {
        val redirectedPages = redirectPageTitles[wikiPage.title]
        val currentDocument = Document()
        val titleFieldType = FieldType(StringField.TYPE_STORED)

        titleFieldType.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS)
        currentDocument.add(Field("title", wikiPage.title, titleFieldType))

        if (redirectedPages != null) {
            for (title in redirectedPages){
                currentDocument.add(Field("title", title, titleFieldType))
            }
        }

        val categoryFieldType = FieldType(StringField.TYPE_STORED)
        categoryFieldType.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS)
        if (wikiPage.categories.size == 0) {
            //if there is not category at all we have issues at querries
            currentDocument.add(Field("category", "", categoryFieldType))
        } else {
            for (category in wikiPage.categories) {
                currentDocument.add(Field("category", category, categoryFieldType))
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

fun runUserQuery() {
    val directory: Directory = FSDirectory.open(Path.of(indexPath))
    val reader = DirectoryReader.open(directory)

    print("text: ")
    val content = readlnOrNull()

    val fields = arrayOf("content")

    val queryString = "${content}"

    val parser = MultiFieldQueryParser(fields, getAnalyzer())

    val query: Query = parser.parse(queryString)

    val hitsPerPage = 10

    val searcher = IndexSearcher(reader)
    val docs = searcher.search(query, hitsPerPage)
    val hits = docs.scoreDocs
    println("Found " + hits.size + " hits.")
    for (i in hits.indices) {
        val docId = hits[i].doc
        val d = searcher.doc(docId)
        val documentTitles = d.getValues("title").asList()
        println((i + 1).toString() + ". " + documentTitles.joinToString())
    }
}

fun runDefaultQuestions() {
    BufferedReader(FileReader(questionsFile)).use { reader ->
        var line: String?

        var listResults: List<Int> = listOf()

        val pattern = Regex("[.,:;!?-]")

        while (reader.readLine().also { line = it } != null) {
            if (line.isNullOrBlank()) {
                continue
            }
            val category = line!!.trim()
                .replace(pattern, "")
            reader.readLine().also { line = it }
            val clue = line!!.trim()
                .replace(pattern, "")
            reader.readLine().also { line = it }
            val answer = line!!.trim()
            listResults += runSingleQuery(category, clue, answer)
        }

        val formattedMRR = String.format("%.3f", computeMRR(listResults))
        println("The MRR is : ${formattedMRR}")
        println("The correct number of top positions : ${listResults.filter { x-> x == 1 }.count()}/100")
    }
}

fun runSingleQuery(category: String, clue: String, answer: String): Int {
    try {
        val directory: Directory = FSDirectory.open(Path.of(indexPath))
        val reader = DirectoryReader.open(directory)

        var newCategory = category.substringBefore("(")

        var fields: Array<String> = arrayOf("content", "category")

        var queryString = "($clue) OR ($newCategory)"

        val parser = MultiFieldQueryParser(fields, getAnalyzer())

        val query: Query = parser.parse(queryString)

        val hitsPerPage = 30

        val searcher = IndexSearcher(reader)
        val docs = searcher.search(query, hitsPerPage)
        val hits = docs.scoreDocs
        println("Found " + hits.size + " hits.")

        var correctAnswers = answer.split("|")
        var result = 0

        for (i in hits.indices) {
            val docId = hits[i].doc
            val d = searcher.doc(docId)
            val documentTitles = d.getValues("title").asList()
            println((i + 1).toString() + ". " + documentTitles.joinToString())
            if (correctAnswers.intersect(documentTitles).isNotEmpty() && result == 0)
                result = i+1
        }

        println("clue: $clue")
        println("category: $category")
        println("expected answer: $answer\n")

        return result
    } catch (ex: RuntimeException) {
        println(ex.message)
        return 0
    }
}

fun processFile(
    filePath: String,
    pageSet: MutableSet<WikiPage>,
    redirectPageTitles: MutableMap<String, MutableList<String>>
): PageParseResult {
    val auxPageSet: MutableSet<WikiPage> = TreeSet(CustomComparator())
    val auxRedirectPageTitles: MutableMap<String, MutableList<String>> = mutableMapOf()
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
                val redirectPage = getRedirectPageTitle(line!!)
                if (!auxRedirectPageTitles.containsKey(redirectPage))
                    auxRedirectPageTitles[redirectPage] = mutableListOf()
                auxRedirectPageTitles[redirectPage]?.add(page.title)
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
    for (item in auxRedirectPageTitles){
        if (redirectPageTitles.containsKey(item.key)) {
            redirectPageTitles[item.key]?.addAll(item.value)
        }
        else {
            redirectPageTitles[item.key] = item.value
        }
    }

    return PageParseResult(pageSet, redirectPageTitles)
}

fun getAnalyzer(): Analyzer {
    return EnglishAnalyzer()
}

fun computeMRR(ranks: List<Int>): Double {
    if (ranks.isEmpty()) {
        throw IllegalArgumentException("Input list of ranks is empty.")
    }

    // if the rank is 0, it is considered the document was not found, so we use as reciprocal rank 0
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

fun deleteFilesFromDirectory(directory: String) {
    val folder = File(directory)
    if (!folder.exists()) {
        return
    }

    if (!folder.isDirectory()){
        throw RuntimeException("Folder is not directory")
    }

    val files = folder.listFiles() ?: return
    for (file in files) {
        if (file.isFile) {
            file.delete()
        }
    }
}