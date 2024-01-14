package com.data.mining

import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.store.ByteBuffersDirectory
import org.apache.lucene.store.Directory


fun main() {
    val analyzer = StandardAnalyzer()
    val index: Directory = ByteBuffersDirectory()
    val config = IndexWriterConfig(analyzer)
    val indexWriter = IndexWriter(index, config)

}