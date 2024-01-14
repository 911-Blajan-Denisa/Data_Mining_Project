package com.data.mining

import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.document.*
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.IndexOptions
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.queryparser.classic.QueryParser
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.Query
import org.apache.lucene.store.Directory
import org.apache.lucene.store.FSDirectory
import org.apache.lucene.util.ResourceLoader
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.nio.file.FileSystems
import java.nio.file.Path
import java.util.*
import java.util.regex.Matcher
import java.util.regex.Pattern


val indexPath = "/home/csabi100/Personal/Master/Anul1/Sem1/DataMining/project/Data_Mining_Project/src/main/resources/tmp"

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

fun runQuestions() {
    val questionsFile = ResourceLoader::class.java.classLoader.getResource("questions.txt")?.path
        ?: throw RuntimeException("Resource not found")

    BufferedReader(FileReader(questionsFile)).use { reader ->
        var line: String?

        // Read lines from the file
        while (reader.readLine().also { line = it } != null) {
            if (line.isNullOrBlank()) {
                continue
            }
            val category = line!!.trim()
                .replace("-", "\\-")
                .replace("!", "\\!")
            reader.readLine().also { line = it }
            val content = line!!.trim()
                .replace("-", "\\-")
                .replace("!", "\\!")
            reader.readLine().also { line = it }
            val expectedResult = line!!.trim()
                .replace("-", "\\-")
                .replace("!", "\\!")
            runSingleQuery(category, content, expectedResult)
        }
    }
}

fun runSingleQuery(category: String, content: String, expectedResult: String) {
    try {
        val directory: Directory = FSDirectory.open(Path.of(indexPath))
        val reader = DirectoryReader.open(directory)

        val analyzer = StandardAnalyzer()
        val special = "content:\"$content\" OR category:\"$category\""
        val q: Query = QueryParser("content", analyzer).parse(special)
        val hitsPerPage = 10

        val searcher = IndexSearcher(reader)
        val docs = searcher.search(q, hitsPerPage)
        val hits = docs.scoreDocs
        println("Found " + hits.size + " hits.")
        for (i in hits.indices) {
            val docId = hits[i].doc
            val d = searcher.doc(docId)
            println((i + 1).toString() + ". " + "\t" + d["title"])
        }

        println("content: $content")
        println("expected result: $expectedResult\n")
    } catch (ex: RuntimeException) {
        println(ex.message)
    }

}

fun runQuery() {
    val directory: Directory = FSDirectory.open(Path.of(indexPath))
    val reader = DirectoryReader.open(directory)

    print("text: ")
    val queryStr = readlnOrNull()
    val analyzer = StandardAnalyzer()
    val q: Query = QueryParser("content", analyzer).parse(queryStr)
    val hitsPerPage = 10

    val searcher = IndexSearcher(reader)
    val docs = searcher.search(q, hitsPerPage)
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

    val analyzer = StandardAnalyzer()
    val indexPath = FileSystems.getDefault()
        .getPath(indexPath)
    val index: Directory = FSDirectory.open(indexPath)

    val config = IndexWriterConfig(analyzer)
    val indexWriter = IndexWriter(index, config)

    val files = getFilesFromDirectory(directory)

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
    return input.substring("CATEGORIES:".length, input.length).split(",").toMutableList()
}

fun isRedirectLine(input: String?): Boolean {
    if (input == null) {
        return false
    }
    return input.startsWith("#REDIRECT") || input.startsWith("#redirect")
}

fun getRedirectPageTitle(input: String): String {
    return input.substring("#REDIRECT".length, input.length)
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