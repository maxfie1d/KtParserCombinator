package parser

data class Result<T>(val value: T, val remaining: String)
typealias  Parser<T> = (String) -> Result<T>

fun pchar(charToMatch: Char): (String) -> Result<Char> {
    val innerFn = { str: String ->
        if (str.isNullOrEmpty()) {
            val message = "No more input"
            throw Exception(message)
        } else {
            val first = str[0]
            if (first == charToMatch) {
                val remaining = str.substring(1)
                Result(charToMatch, remaining)
            } else {
                val message = String.format("Expecting '%c'. Got '%c'", charToMatch, first)
                throw  Exception(message)
            }
        }
    }

    return innerFn
}

fun <T> run(parser: Parser<T>, str: String): Result<T> {
    return parser(str)
}

fun <T1, T2> andThen(parser1: Parser<T1>, parser2: Parser<T2>): Parser<Pair<T1, T2>> {
    val innerFn = { str: String ->
        // 1つ目のパーサーを適用
        val result1 = run(parser1, str)
        // 2つ目のパーサーを適用
        val result2 = run(parser2, result1.remaining)

        // パース結果を結合
        val newValue = Pair(result1.value, result2.value)
        Result(newValue, result2.remaining)
    }

    return innerFn
}

fun <T> orElse(parser1: Parser<T>, parser2: Parser<T>): Parser<T> {
    val innerFn = { str: String ->
        try {
            val result1 = run(parser1, str)
            result1
        } catch (ex1: Exception) {
            val result2 = run(parser2, str)
            result2
        }
    }

    return innerFn
}

fun <T> choice(listOfParsers: List<Parser<T>>): Parser<T> {
    return listOfParsers.reduce { p1, p2 -> orElse(p1, p2) }
}

fun anyOf(listOfChars: List<Char>): Parser<Char> {
    val parsers = listOfChars.map(::pchar)
    return choice(parsers)
}

fun <T1, T2> mapP(f: (T1) -> T2, parser: Parser<T1>): Parser<T2> {
    val innerFn = { str: String ->
        val result = run(parser, str)

        val newValue = f(result.value)
        Result(newValue, result.remaining)
    }

    return innerFn
}

fun <T> returnP(x: T): Parser<T> {
    val innerFn = { str: String ->
        Result(x, str)
    }

    return innerFn
}

fun <T1, T2> applyP(fP: Parser<(T1) -> T2>, xP: Parser<T1>): Parser<T2> {
    val andThen = andThen(fP, xP)
    val f = fun(pair: Pair<(T1) -> T2, T1>): T2 {
        val (f, x) = pair
        return f(x)
    }

    return mapP(f, andThen)
}

fun <T1, T2, T3> lift2(f: (T1) -> (T2) -> T3, xP: Parser<T1>, yP: Parser<T2>): Parser<T3> {
    val p1 = applyP(returnP(f), xP)
    val p2 = applyP(p1, yP)
    return p2
}

fun <T> sequence(parserList: List<Parser<T>>): Parser<List<T>> {
    val innerFn = { str: String ->
        var s = str

        val mapped = parserList.map { p ->
            val result = run(p, s)
            s = result.remaining
            result.value
        }

        Result(mapped, s)
    }

    return innerFn
}

// tailrec: 末尾再帰
tailrec fun <T> sequenceRec(parserList: List<Parser<T>>): Parser<ArrayList<T>> {
    val cons = { head: T ->
        { tail: ArrayList<T> ->
            tail.add(0, head)
            tail
        }
    }

    if (parserList.size == 0) {
        val emptyList = arrayListOf<T>()
        return returnP(emptyList)
    } else {
        val head = parserList[0]
        val tail = parserList.subList(1, parserList.size)

        return lift2(cons, head, sequenceRec(tail))
    }
}
