import java.util.*
import kotlin.random.Random

// Simple Tic-Tac-Toe library + CLI in one file

enum class Mark(val ch: Char) { X('X'), O('O'), EMPTY(' ') }

class Board {
    private val cells = MutableList(9) { Mark.EMPTY }

    fun copy(): Board {
        val b = Board()
        for (i in 0 until 9) b.cells[i] = this.cells[i]
        return b
    }

    fun availableMoves(): List<Int> = cells.mapIndexedNotNull { i, m -> if (m == Mark.EMPTY) i else null }

    fun place(mark: Mark, pos: Int): Boolean {
        if (pos !in 0..8) return false
        if (cells[pos] != Mark.EMPTY) return false
        cells[pos] = mark
        return true
    }

    fun isFull(): Boolean = cells.all { it != Mark.EMPTY }

    fun winner(): Mark? {
        val lines = listOf(
            listOf(0,1,2), listOf(3,4,5), listOf(6,7,8), // rows
            listOf(0,3,6), listOf(1,4,7), listOf(2,5,8), // cols
            listOf(0,4,8), listOf(2,4,6)                 // diags
        )
        for (ln in lines) {
            val (a,b,c) = ln
            if (cells[a] != Mark.EMPTY && cells[a] == cells[b] && cells[b] == cells[c]) return cells[a]
        }
        return null
    }

    fun gameResult(): GameResult {
        val w = winner()
        return when {
            w == Mark.X -> GameResult.Win(Mark.X)
            w == Mark.O -> GameResult.Win(Mark.O)
            isFull() -> GameResult.Draw
            else -> GameResult.Ongoing
        }
    }

    fun printWithIndices() {
        println("Board (positions 1..9):")
        for (r in 0..2) {
            val row = (0..2).map { c -> (r*3+c)+1 }
            println(" ${row[0]} | ${row[1]} | ${row[2]}")
            if (r < 2) println("---+---+---")
        }
    }

    fun printBoard() {
        println()
        for (r in 0..2) {
            val row = (0..2).map { c -> cells[r*3+c].ch }
            println(" ${row[0]} | ${row[1]} | ${row[2]}")
            if (r < 2) println("---+---+---")
        }
        println()
    }

    override fun toString(): String = cells.joinToString(separator = "") { it.ch.toString() }

    // exposing for AI evaluation
    operator fun get(i: Int) = cells[i]
    operator fun set(i: Int, v: Mark) { cells[i] = v }
}

sealed class GameResult {
    object Ongoing : GameResult()
    object Draw : GameResult()
    data class Win(val winner: Mark) : GameResult()
}

interface Player {
    val mark: Mark
    val name: String
    fun nextMove(board: Board): Int
}

class HumanPlayer(override val name: String, override val mark: Mark, private val scanner: Scanner = Scanner(System.`in`)) : Player {
    override fun nextMove(board: Board): Int {
        while (true) {
            print("$name ($mark) - enter position 1..9: ")
            val line = scanner.nextLine().trim()
            val pos = line.toIntOrNull()?.minus(1)
            if (pos == null || pos !in 0..8) {
                println("Invalid input. Please enter a number from 1 to 9.")
                continue
            }
            if (board[pos] != Mark.EMPTY) {
                println("That position is already taken. Choose another.")
                continue
            }
            return pos
        }
    }
}

class RandomComputerPlayer(override val name: String, override val mark: Mark) : Player {
    private val rng = Random.Default
    override fun nextMove(board: Board): Int {
        val moves = board.availableMoves()
        return moves[rng.nextInt(moves.size)]
    }
}

class MinimaxComputerPlayer(override val name: String, override val mark: Mark) : Player {
    private val opponent: Mark = if (mark == Mark.X) Mark.O else Mark.X

    override fun nextMove(board: Board): Int {
        val moves = board.availableMoves()
        if (moves.size == 9) return 4 // take center if first
        var bestScore = Int.MIN_VALUE
        var bestMove = moves.first()
        for (m in moves) {
            board[m] = mark
            val score = minimax(board, false)
            board[m] = Mark.EMPTY
            if (score > bestScore) {
                bestScore = score
                bestMove = m
            }
        }
        return bestMove
    }

    private fun minimax(board: Board, isMax: Boolean): Int {
        when (val res = board.gameResult()) {
            is GameResult.Win -> return when (res.winner) {
                mark -> 10
                opponent -> -10
                else -> 0
            }
            GameResult.Draw -> return 0
            GameResult.Ongoing -> { /* continue */ }
        }
        val moves = board.availableMoves()
        if (isMax) {
            var best = Int.MIN_VALUE
            for (m in moves) {
                board[m] = mark
                val valScore = minimax(board, false)
                board[m] = Mark.EMPTY
                best = maxOf(best, valScore)
            }
            return best
        } else {
            var best = Int.MAX_VALUE
            for (m in moves) {
                board[m] = opponent
                val valScore = minimax(board, true)
                board[m] = Mark.EMPTY
                best = minOf(best, valScore)
            }
            return best
        }
    }
}

class Game(private val playerX: Player, private val playerO: Player) {
    private val board = Board()
    private var current: Player = playerX

    fun play() {
        println("\nStarting Tic-Tac-Toe between ${playerX.name} (X) and ${playerO.name} (O)")
        println("Initial board:")
        board.printWithIndices()
        board.printBoard()

        while (true) {
            val move = try { current.nextMove(board) } catch (e: Exception) {
                println("Error getting move from ${current.name}: ${e.message}")
                return
            }
            val success = board.place(current.mark, move)
            if (!success) {
                println("Invalid move by ${current.name}. Skipping turn.")
            }
            println("${current.name} (${current.mark}) placed at ${move + 1}:")
            board.printBoard()

            when (val res = board.gameResult()) {
                is GameResult.Win -> {
                    println("Game over: ${res.winner} wins!")
                    val winnerName = if (res.winner == playerX.mark) playerX.name else playerO.name
                    println("Winner: $winnerName (${res.winner})")
                    return
                }
                GameResult.Draw -> {
                    println("Game over: Draw!")
                    return
                }
                GameResult.Ongoing -> {} // continue
            }

            current = if (current === playerX) playerO else playerX
        }
    }
}

// ----------------- CLI bootstrapping -----------------

fun choosePlayer(scanner: Scanner, defaultName: String, mark: Mark): Player {
    while (true) {
        print("Enter name for $defaultName ($mark): ")
        val name = scanner.nextLine().trim().ifEmpty { defaultName }
        print("Type for $name? (h = human, r = random computer, a = smart AI) [h]: ")
        val t = scanner.nextLine().trim().lowercase().ifEmpty { "h" }
        return when (t.first()) {
            'h' -> HumanPlayer(name, mark, scanner)
            'r' -> RandomComputerPlayer(name, mark)
            'a' -> MinimaxComputerPlayer(name, mark)
            else -> {
                println("Unknown type '$t'. Use h, r or a.")
                continue
            }
        }
    }
}

// real usage
fun main() {
    val scanner = Scanner(System.`in`)
    println("Welcome to Tic-Tac-Toe CLI")

    // asking for names of players
    val playerX = choosePlayer(scanner, "Player X", Mark.X)
    val playerO = choosePlayer(scanner, "Player O", Mark.O)

    // printing name of user after he types his name
    val game = Game(playerX, playerO)
    game.play()

    println("Thanks for playing!")
}
