package io.legado.app.model.webbook

import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.SearchBook
import io.legado.app.help.BookHelp
import io.legado.app.model.Debug
import io.legado.app.model.analyzeRule.AnalyzeRule
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.utils.NetworkUtils

object BookList {

    @Throws(Exception::class)
    fun analyzeBookList(
        body: String?,
        bookSource: BookSource,
        analyzeUrl: AnalyzeUrl,
        baseUrl: String,
        isSearch: Boolean = true
    ): ArrayList<SearchBook> {
        val bookList = ArrayList<SearchBook>()
        body ?: throw Exception(
//            App.INSTANCE.getString(
//                R.string.error_get_web_content,
//                analyzeUrl.ruleUrl
//            )
                //todo getString
                "error_get_web_content"
        )
        Debug.log(bookSource.bookSourceUrl, "≡获取成功:${analyzeUrl.ruleUrl}")
        val analyzeRule = AnalyzeRule(null)
        analyzeRule.setContent(body, baseUrl)
        bookSource.bookUrlPattern?.let {
            if (baseUrl.matches(it.toRegex())) {
                Debug.log(bookSource.bookSourceUrl, "≡链接为详情页")
                getInfoItem(analyzeRule, bookSource, baseUrl)?.let { searchBook ->
                    searchBook.infoHtml = body
                    bookList.add(searchBook)
                }
                return bookList
            }
        }
        val collections: List<Any>
        var reverse = false
        val bookListRule = when {
            isSearch -> bookSource.getSearchRule()
            bookSource.getExploreRule().bookList.isNullOrBlank() -> bookSource.getSearchRule()
            else -> bookSource.getExploreRule()
        }
        var ruleList: String = bookListRule.bookList ?: ""
        if (ruleList.startsWith("-")) {
            reverse = true
            ruleList = ruleList.substring(1)
        }
        if (ruleList.startsWith("+")) {
            ruleList = ruleList.substring(1)
        }
        Debug.log(bookSource.bookSourceUrl, "┌获取书籍列表")
        collections = analyzeRule.getElements(ruleList)
        if (collections.isEmpty() && bookSource.bookUrlPattern.isNullOrEmpty()) {
            Debug.log(bookSource.bookSourceUrl, "└列表为空,按详情页解析")
            getInfoItem(analyzeRule, bookSource, baseUrl)?.let { searchBook ->
                searchBook.infoHtml = body
                bookList.add(searchBook)
            }
        } else {
            val ruleName = analyzeRule.splitSourceRule(bookListRule.name)
            val ruleBookUrl = analyzeRule.splitSourceRule(bookListRule.bookUrl)
            val ruleAuthor = analyzeRule.splitSourceRule(bookListRule.author)
            val ruleCoverUrl = analyzeRule.splitSourceRule(bookListRule.coverUrl)
            val ruleIntro = analyzeRule.splitSourceRule(bookListRule.intro)
            val ruleKind = analyzeRule.splitSourceRule(bookListRule.kind)
            val ruleLastChapter = analyzeRule.splitSourceRule(bookListRule.lastChapter)
            val ruleWordCount = analyzeRule.splitSourceRule(bookListRule.wordCount)
            Debug.log(bookSource.bookSourceUrl, "└列表大小:${collections.size}")
            for ((index, item) in collections.withIndex()) {
                getSearchItem(
                    item, analyzeRule, bookSource, baseUrl, index == 0,
                    ruleName = ruleName, ruleBookUrl = ruleBookUrl, ruleAuthor = ruleAuthor,
                    ruleCoverUrl = ruleCoverUrl, ruleIntro = ruleIntro, ruleKind = ruleKind,
                    ruleLastChapter = ruleLastChapter, ruleWordCount = ruleWordCount
                )?.let { searchBook ->
                    if (baseUrl == searchBook.bookUrl) {
                        searchBook.infoHtml = body
                    }
                    bookList.add(searchBook)
                }
            }
            if (reverse) {
                bookList.reverse()
            }
        }
        return bookList
    }

    private fun getInfoItem(
        analyzeRule: AnalyzeRule,
        bookSource: BookSource,
        baseUrl: String
    ): SearchBook? {
        val searchBook = SearchBook()
        searchBook.bookUrl = baseUrl
        searchBook.origin = bookSource.bookSourceUrl
        searchBook.originName = bookSource.bookSourceName
        searchBook.originOrder = bookSource.customOrder
        searchBook.type = bookSource.bookSourceType
        analyzeRule.book = searchBook
        with(bookSource.getBookInfoRule()) {
            init?.let {
                if (it.isNotEmpty()) {
                    Debug.log(bookSource.bookSourceUrl, "≡执行详情页初始化规则")
                    analyzeRule.setContent(analyzeRule.getElement(it))
                }
            }
            Debug.log(bookSource.bookSourceUrl, "┌获取书名")
            searchBook.name = analyzeRule.getString(name)
            Debug.log(bookSource.bookSourceUrl, "└${searchBook.name}")
            if (searchBook.name.isNotEmpty()) {
                Debug.log(bookSource.bookSourceUrl, "┌获取作者")
                searchBook.author = BookHelp.formatAuthor(analyzeRule.getString(author))
                Debug.log(bookSource.bookSourceUrl, "└${searchBook.author}")
                Debug.log(bookSource.bookSourceUrl, "┌获取分类")
                searchBook.kind = analyzeRule.getString(kind)
                Debug.log(bookSource.bookSourceUrl, "└${searchBook.kind}")
                Debug.log(bookSource.bookSourceUrl, "┌获取字数")
                searchBook.wordCount = analyzeRule.getString(wordCount)
                Debug.log(bookSource.bookSourceUrl, "└${searchBook.wordCount}")
                Debug.log(bookSource.bookSourceUrl, "┌获取最新章节")
                searchBook.latestChapterTitle = analyzeRule.getString(lastChapter)
                Debug.log(bookSource.bookSourceUrl, "└${searchBook.latestChapterTitle}")
                Debug.log(bookSource.bookSourceUrl, "┌获取简介")
                searchBook.intro = analyzeRule.getString(intro)
                Debug.log(bookSource.bookSourceUrl, "└${searchBook.intro}", true)
                Debug.log(bookSource.bookSourceUrl, "┌获取封面链接")
                searchBook.coverUrl = analyzeRule.getString(coverUrl, true)
                Debug.log(bookSource.bookSourceUrl, "└${searchBook.coverUrl}")
                return searchBook
            }
        }
        return null
    }

    private fun getSearchItem(
        item: Any,
        analyzeRule: AnalyzeRule,
        bookSource: BookSource,
        baseUrl: String,
        log: Boolean,
        ruleName: List<AnalyzeRule.SourceRule>,
        ruleBookUrl: List<AnalyzeRule.SourceRule>,
        ruleAuthor: List<AnalyzeRule.SourceRule>,
        ruleKind: List<AnalyzeRule.SourceRule>,
        ruleCoverUrl: List<AnalyzeRule.SourceRule>,
        ruleWordCount: List<AnalyzeRule.SourceRule>,
        ruleIntro: List<AnalyzeRule.SourceRule>,
        ruleLastChapter: List<AnalyzeRule.SourceRule>
    ): SearchBook? {
        val searchBook = SearchBook()
        searchBook.origin = bookSource.bookSourceUrl
        searchBook.originName = bookSource.bookSourceName
        searchBook.type = bookSource.bookSourceType
        searchBook.originOrder = bookSource.customOrder
        analyzeRule.book = searchBook
        analyzeRule.setContent(item)
        Debug.log(bookSource.bookSourceUrl, "┌获取书名", log)
        searchBook.name = analyzeRule.getString(ruleName)
        Debug.log(bookSource.bookSourceUrl, "└${searchBook.name}", log)
        if (searchBook.name.isNotEmpty()) {
            Debug.log(bookSource.bookSourceUrl, "┌获取作者", log)
            searchBook.author = BookHelp.formatAuthor(analyzeRule.getString(ruleAuthor))
            Debug.log(bookSource.bookSourceUrl, "└${searchBook.author}", log)
            Debug.log(bookSource.bookSourceUrl, "┌获取分类", log)
            searchBook.kind = analyzeRule.getString(ruleKind)
            Debug.log(bookSource.bookSourceUrl, "└${searchBook.kind}", log)
            Debug.log(bookSource.bookSourceUrl, "┌获取字数", log)
            searchBook.wordCount = analyzeRule.getString(ruleWordCount)
            Debug.log(bookSource.bookSourceUrl, "└${searchBook.wordCount}", log)
            Debug.log(bookSource.bookSourceUrl, "┌获取最新章节", log)
            searchBook.latestChapterTitle = analyzeRule.getString(ruleLastChapter)
            Debug.log(bookSource.bookSourceUrl, "└${searchBook.latestChapterTitle}", log)
            Debug.log(bookSource.bookSourceUrl, "┌获取简介", log)
            searchBook.intro = analyzeRule.getString(ruleIntro)
            Debug.log(bookSource.bookSourceUrl, "└${searchBook.intro}", log, true)
            Debug.log(bookSource.bookSourceUrl, "┌获取封面链接", log)
            analyzeRule.getString(ruleCoverUrl).let {
                if (it.isNotEmpty()) searchBook.coverUrl = NetworkUtils.getAbsoluteURL(baseUrl, it)
            }
            Debug.log(bookSource.bookSourceUrl, "└${searchBook.coverUrl}", log)
            Debug.log(bookSource.bookSourceUrl, "┌获取详情页链接", log)
            searchBook.bookUrl = analyzeRule.getString(ruleBookUrl, true)
            if (searchBook.bookUrl.isEmpty()) {
                searchBook.bookUrl = baseUrl
            }
            Debug.log(bookSource.bookSourceUrl, "└${searchBook.bookUrl}", log)
            return searchBook
        }
        return null
    }

}