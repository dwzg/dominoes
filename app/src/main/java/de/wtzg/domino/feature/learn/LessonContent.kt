package de.wtzg.domino.feature.learn

import de.wtzg.domino.engine.GameMode
import de.wtzg.domino.feature.learn.LessonCategory.ADVANCED
import de.wtzg.domino.feature.learn.LessonCategory.BASICS
import de.wtzg.domino.feature.learn.LessonCategory.SCORING
import de.wtzg.domino.feature.learn.LessonCategory.STRATEGY

/**
 * All lesson content shown in the Learn section.
 *
 * Every Table below has been worked out by hand: the comment above it lists the open ends
 * (and, for scoring games, the count) after the last move.
 */
object Lessons {

    // ---------------------------------------------------------------------------------------------
    // BASICS
    // ---------------------------------------------------------------------------------------------

    private val basicsTiles = Lesson(
        id = "basics-tiles",
        category = BASICS,
        title = "The tiles and the set",
        summary = "28 tiles, numbers 0 to 6, and why each number appears exactly 7 times.",
        blocks = listOf(
            LessonBlock.Text(
                "A domino tile has two halves. Each half shows a number from 0 (blank) to 6. " +
                    "The dots are called pips. A double-six set has one tile for every pair of numbers: 28 tiles in total.",
            ),
            LessonBlock.Text(
                "A tile with the same number on both halves is a double. There are seven doubles, from 0|0 to 6|6. " +
                    "Doubles are special: they are placed crosswise on the table, and the highest double starts each hand.",
            ),
            LessonBlock.Hand(
                listOf(t(0, 0), t(1, 1), t(2, 2), t(3, 3), t(4, 4), t(5, 5), t(6, 6)),
                "The seven doubles. Each one exists only once.",
            ),
            LessonBlock.Text(
                "Every number appears on exactly 7 tiles: once with each number from 0 to 6, including its own double. " +
                    "This is the most useful fact in dominoes. If you can see six 5s, you know exactly where the last one is not.",
            ),
            LessonBlock.Hand(
                listOf(t(5, 0), t(5, 1), t(5, 2), t(5, 3), t(5, 4), t(5, 5), t(5, 6)),
                "All seven tiles with a 5. There are no others.",
            ),
            LessonBlock.Tip("The whole set holds 168 pips. The heaviest tile is 6|6 (12 pips), the lightest is 0|0 (0 pips)."),
        ),
    )

    private val basicsDraw = Lesson(
        id = "basics-draw",
        category = BASICS,
        title = "How Draw works",
        summary = "The classic game: match an open end, and draw from the boneyard when you can't.",
        practice = GameMode.DRAW,
        blocks = listOf(
            LessonBlock.Text(
                "With 2 players, each of you gets 7 tiles. With 3 or 4 players, everyone gets 5. " +
                    "The rest lie face down: this is the boneyard. The player with the highest double opens the hand by playing it.",
            ),
            LessonBlock.Text(
                "Turns go clockwise. On your turn, play one tile that matches a number at one of the two open ends. " +
                    "The matching halves touch, and the other half becomes the new open end.",
            ),
            // ends: west 2 (6|2 played on 6), east 4 (6|4 played on 6)
            LessonBlock.Table(
                listOf(east(6, 6), east(6, 4), west(6, 2)),
                "6|6 opened. Then 6|4 went east and 6|2 went west. The open ends are now 2 and 4.",
            ),
            LessonBlock.Hand(
                listOf(t(2, 5), t(1, 3), t(0, 0)),
                "With this hand you can play 2|5 on the 2 end. The 5 would then be open.",
            ),
            LessonBlock.Text(
                "If no tile in your hand fits, draw from the boneyard, one tile at a time, until you can play. " +
                    "If the boneyard is empty, you pass.",
            ),
            LessonBlock.Text(
                "The first player to play their last tile wins the hand and scores the pips left in all opponents' hands. " +
                    "In this app, the first to 60 points wins the match.",
            ),
        ),
    )

    private val basicsBlock = Lesson(
        id = "basics-block",
        category = BASICS,
        title = "How Block differs",
        summary = "Same as Draw, but there is no drawing: if you can't play, you pass.",
        practice = GameMode.BLOCK,
        blocks = listOf(
            LessonBlock.Text(
                "Block plays like Draw with one change: you never draw. If no tile in your hand fits an open end, you pass " +
                    "and the next player goes.",
            ),
            LessonBlock.Text(
                "Everyone gets 7 tiles, whatever the number of players. With 2 or 3 players, the tiles nobody was dealt " +
                    "stay face down and out of play for the whole hand.",
            ),
            // ends: west 0 (5|0 on 5), east 2 (5|2 on 5)
            LessonBlock.Table(
                listOf(east(5, 5), east(5, 2), west(5, 0)),
                "The open ends are 0 and 2. A player with no 0 and no 2 must pass.",
            ),
            LessonBlock.Hand(
                listOf(t(1, 4), t(3, 6), t(4, 4)),
                "No 0 and no 2 here. In Draw you would draw; in Block you simply pass.",
            ),
            LessonBlock.Text(
                "Because nobody can draw, hands get stuck much more often. When every player passes in a row, " +
                    "the game is blocked and the lowest pip total wins. The first to 60 wins the match.",
            ),
            LessonBlock.Tip("In Block, every pass tells you something. Your opponent just showed you two numbers they don't have."),
        ),
    )

    private val basicsTable = Lesson(
        id = "basics-table",
        category = BASICS,
        title = "Reading the table",
        summary = "Open ends, crosswise doubles and how to see what can be played.",
        blocks = listOf(
            LessonBlock.Text(
                "The line of tiles on the table has two open ends, one on each side. Only these two numbers matter for your next play. " +
                    "Everything in between is just history (but useful history for counting).",
            ),
            LessonBlock.Text(
                "Doubles are placed crosswise, across the line. A double doesn't change the open number: " +
                    "playing 3|3 on a 3 leaves a 3 open.",
            ),
            // ends: west 1 (6|1 on 6), east 3 (3|3 on 3 leaves 3)
            LessonBlock.Table(
                listOf(east(6, 6), east(6, 3), east(3, 3), west(6, 1)),
                "6|6 opened, 6|3 and the double 3|3 went east, 6|1 went west. The open ends are 1 and 3.",
            ),
            LessonBlock.Text(
                "To see what is still possible, count the numbers on the table. Here three 6s are down (6|6 counts as one tile), " +
                    "two tiles with a 3 and one with a 1.",
            ),
            LessonBlock.Tip("Before every turn, say the two open numbers to yourself. It sounds simple, and it prevents most mistakes."),
        ),
    )

    private val basicsScoring = Lesson(
        id = "basics-scoring",
        category = BASICS,
        title = "Scoring a hand and blocked games",
        summary = "Who wins a hand, what they score, and what happens when nobody can play.",
        blocks = listOf(
            LessonBlock.Text(
                "A hand ends in one of two ways. Either someone plays their last tile (they \"domino\"), " +
                    "or every player passes in a row and the game is blocked.",
            ),
            LessonBlock.Text(
                "The winner of the hand scores the pips left in all opponents' hands. Your own leftover tiles don't cost you points " +
                    "directly, but they are points you hand to someone else when they win.",
            ),
            LessonBlock.Hand(
                listOf(t(4, 5), t(2, 0)),
                "You dominoed and your opponent is left with these: 9 + 2 = 11 points for you.",
            ),
            LessonBlock.Text(
                "In a blocked game, everyone adds up the pips in their hand. The lowest total wins the hand and scores all opponents' pips. " +
                    "If the lowest totals are tied, nobody scores.",
            ),
            LessonBlock.Hand(
                listOf(t(1, 2), t(0, 3)),
                "Blocked: you hold 3 + 3 = 6 pips. Your opponent holds 5|6 = 11. You win the hand and score 11.",
            ),
            LessonBlock.Tip("Targets in this app: Draw 60, Block 60, All Fives 100, All Threes 90."),
        ),
    )

    // ---------------------------------------------------------------------------------------------
    // STRATEGY
    // ---------------------------------------------------------------------------------------------

    private val strategyPips = Lesson(
        id = "strategy-pips",
        category = STRATEGY,
        title = "Count your pips",
        summary = "Heavy tiles are a risk. Know your total and get rid of weight early.",
        practice = GameMode.DRAW,
        blocks = listOf(
            LessonBlock.Text(
                "Whenever someone else wins a hand, the pips in your hand become their points. " +
                    "Heavy tiles like 6|6 or 6|5 are expensive to be caught with.",
            ),
            LessonBlock.Hand(
                listOf(t(6, 6), t(6, 5), t(5, 4), t(1, 0), t(2, 1)),
                "This hand holds 12 + 11 + 9 + 1 + 3 = 36 pips. Most of it sits in the first three tiles.",
            ),
            LessonBlock.Text(
                "When two plays look equally good, play the heavier tile. It lowers your total and costs you less if the hand goes wrong.",
            ),
            LessonBlock.Text(
                "Keep a running total of your hand. It tells you whether a blocked game would be good or bad for you.",
            ),
            LessonBlock.Tip("Light tiles are your safety net late in the hand. Heavy tiles are your liability."),
        ),
    )

    private val strategyDoubles = Lesson(
        id = "strategy-doubles",
        category = STRATEGY,
        title = "Play doubles early",
        summary = "A double only fits one number. Don't let it get stranded.",
        blocks = listOf(
            LessonBlock.Text(
                "A normal tile like 5|2 fits a 5 or a 2. A double like 5|5 only fits a 5. That makes doubles harder to place.",
            ),
            LessonBlock.Text(
                "Every time another tile with a 5 is played, there is one less way to reach a 5 end. " +
                    "Once the other six 5s are all down, your 5|5 can never be played. It stays in your hand, pips and all.",
            ),
            LessonBlock.Hand(
                listOf(t(5, 5), t(5, 2), t(3, 1), t(6, 4)),
                "If a 5 is open, play 5|5 now. You keep 5|2, so the 5 stays open for you afterwards.",
            ),
            LessonBlock.Text(
                "Doubles also don't change the open number, so playing one is a cheap way to keep an end you like.",
            ),
            LessonBlock.Tip("Heavy doubles first: 6|6 and 5|5 are both hard to place and expensive to keep."),
        ),
    )

    private val strategyOptions = Lesson(
        id = "strategy-options",
        category = STRATEGY,
        title = "Keep your options open",
        summary = "Prefer plays that leave you a follow-up on your next turn.",
        practice = GameMode.DRAW,
        blocks = listOf(
            LessonBlock.Text(
                "Before you play, look at the number your tile will leave open. Do you hold another tile with that number? " +
                    "If so, you have a follow-up.",
            ),
            // opening only: ends west 5, east 3
            LessonBlock.Table(
                listOf(east(5, 3)),
                "The open ends are 5 and 3.",
            ),
            LessonBlock.Hand(
                listOf(t(5, 2), t(2, 4), t(3, 6), t(0, 0)),
                "Your hand. 3|6 would leave a 6, and you hold no other 6. 5|2 leaves a 2, and you still hold 2|4.",
            ),
            // ends: west 2 (5|2 on 5), east 3
            LessonBlock.Table(
                listOf(east(5, 3), west(5, 2)),
                "After 5|2 the ends are 2 and 3. You can play 2|4 or 3|6 next turn.",
            ),
            LessonBlock.Text(
                "Try to hold tiles of as many different numbers as you can. A hand with five different numbers rarely has to draw or pass.",
            ),
            LessonBlock.Tip("Ask \"what can I play next turn?\" before every move, not just \"what can I play now?\""),
        ),
    )

    private val strategyControl = Lesson(
        id = "strategy-control",
        category = STRATEGY,
        title = "Control a number",
        summary = "Hold many tiles of one number and keep that number open.",
        blocks = listOf(
            LessonBlock.Text(
                "If you hold several tiles with the same number, that number is yours. " +
                    "Whenever it is open, you can play and your opponents probably can't.",
            ),
            LessonBlock.Hand(
                listOf(t(2, 0), t(2, 3), t(2, 5), t(2, 6), t(1, 4)),
                "Four 2s. Only three other 2s exist, so your opponents hold at most three of them.",
            ),
            LessonBlock.Text(
                "Try to leave your strong number open. Play tiles that put a 2 at an end, and keep a 2 in hand to answer when it gets covered.",
            ),
            LessonBlock.Text(
                "The opposite also works: if you hold none of a number, avoid opening it. You'd only be helping the others.",
            ),
            LessonBlock.Tip("Your longest suit is your weapon. Don't spend it all at once."),
        ),
    )

    private val strategyRead = Lesson(
        id = "strategy-read",
        category = STRATEGY,
        title = "Read your opponent",
        summary = "Draws and passes tell you which numbers an opponent is missing.",
        practice = GameMode.DRAW,
        blocks = listOf(
            LessonBlock.Text(
                "When an opponent draws or passes, they had nothing for either open end. Remember those two numbers.",
            ),
            // ends: west 5 (6|5 on 6), east 3 (6|3 on 6)
            LessonBlock.Table(
                listOf(east(6, 6), east(6, 3), west(6, 5)),
                "The ends are 5 and 3, and your opponent had to draw. They held no 5 and no 3.",
            ),
            LessonBlock.Text(
                "Now use it: whenever you can, leave a 5 or a 3 open. In Draw they may have picked one up while drawing, " +
                    "but in Block a pass tells you for sure they are out of those numbers until the end of the hand.",
            ),
            LessonBlock.Text(
                "Also watch what they play. Someone who keeps playing 4s probably has more of them. Someone who plays a double " +
                    "late may have been stuck with it.",
            ),
            LessonBlock.Tip("The Hard AI in this app remembers every draw and pass. You should too."),
        ),
    )

    private val strategyCounting = Lesson(
        id = "strategy-counting",
        category = STRATEGY,
        title = "Count tiles to block",
        summary = "Seven tiles of each number: count what you can see and work out the rest.",
        practice = GameMode.BLOCK,
        blocks = listOf(
            LessonBlock.Text(
                "Each number appears on 7 tiles. Add the ones on the table to the ones in your hand. " +
                    "The rest are with your opponents (or still in the boneyard).",
            ),
            // ends: west 2 (4|6 on 4 leaves 6, then 6|2 leaves 2), east 1 (4|1 on 4)
            LessonBlock.Table(
                listOf(east(4, 4), east(4, 1), west(4, 6), west(6, 2)),
                "Three 4s are on the table: 4|4, 4|1 and 4|6. The open ends are 2 and 1.",
            ),
            LessonBlock.Hand(
                listOf(t(4, 2), t(4, 0), t(4, 3)),
                "You hold three more 4s. That's six of seven. Only 4|5 is somewhere else.",
            ),
            LessonBlock.Text(
                "Play 4|2 on the 2 end and a 4 is open. Your opponent can only answer that end with 4|5. " +
                    "If they lack a 1 as well, they are stuck, and you still hold two 4s to play.",
            ),
            LessonBlock.Tip("Counting is easiest for the number you hold most of. Start there."),
        ),
    )

    private val strategyEndgame = Lesson(
        id = "strategy-endgame",
        category = STRATEGY,
        title = "The endgame",
        summary = "With few tiles left, every play matters: plan your last moves.",
        blocks = listOf(
            LessonBlock.Text(
                "When you are down to two or three tiles, plan the order you will play them in. Make sure the tile you play now " +
                    "leaves an end that your next tile fits.",
            ),
            LessonBlock.Hand(
                listOf(t(3, 1), t(1, 6)),
                "Two tiles left. If a 3 is open, play 3|1: it leaves a 1, and 1|6 can follow it to finish.",
            ),
            LessonBlock.Text(
                "Watch your opponents' tile counts. If someone has one tile left, stop playing for points or position. " +
                    "Block the numbers they need and dump your heaviest tile.",
            ),
            LessonBlock.Text(
                "If you can't win the race, lower the damage: get rid of the heavy tiles first, so the winner scores less.",
            ),
            LessonBlock.Tip("One tile left in an opponent's hand? Look at what they passed on. Keep those numbers open."),
        ),
    )

    // ---------------------------------------------------------------------------------------------
    // SCORING
    // ---------------------------------------------------------------------------------------------

    private val scoringFives = Lesson(
        id = "scoring-fives",
        category = SCORING,
        title = "All Fives basics",
        summary = "Score during play whenever the open ends add up to a multiple of 5.",
        practice = GameMode.ALL_FIVES,
        blocks = listOf(
            LessonBlock.Text(
                "In All Fives you score while you play. After every play, add up the open ends. " +
                    "If the total is 5, 10, 15, 20 and so on, the player who just played scores that total.",
            ),
            // only the opening tile is down: count is its total, 6 + 4 = 10 -> scores 10
            LessonBlock.Table(
                listOf(east(6, 4)),
                "Only one tile is down, so it counts in full: 6 + 4 = 10. The opener scores 10.",
                spinner = true,
            ),
            // ends: west 6, east 2 (4|2 on 4) -> 6 + 2 = 8, no score
            LessonBlock.Table(
                listOf(east(6, 4), east(4, 2)),
                "4|2 goes on the 4. Now the ends are 6 and 2: 8 is not a multiple of 5, so nobody scores.",
                spinner = true,
            ),
            // ends: west 6, east 2|2 double at the end counts 4 -> 6 + 4 = 10 -> scores 10
            LessonBlock.Table(
                listOf(east(6, 4), east(4, 2), east(2, 2)),
                "2|2 goes on the 2. A double at an end counts both halves: 6 + 4 = 10. That play scores 10.",
                spinner = true,
            ),
            LessonBlock.Text(
                "At the end of a hand, the winner scores the opponents' leftover pips rounded to the nearest 5: 12 becomes 10, " +
                    "13 becomes 15. The first to 100 wins, and the match ends the moment someone gets there, even mid-hand.",
            ),
            LessonBlock.Tip("Points during play usually decide All Fives. Look for a scoring play before anything else."),
        ),
    )

    private val scoringSpinner = Lesson(
        id = "scoring-spinner",
        category = SCORING,
        title = "The spinner",
        summary = "The opening double can grow in four directions. Here's how it counts.",
        practice = GameMode.ALL_FIVES,
        blocks = listOf(
            LessonBlock.Text(
                "In All Fives and All Threes, the opening double is a spinner. Once tiles are on both its east and west sides, " +
                    "tiles can also be played on its north and south sides. Then there are up to four open ends.",
            ),
            // ends: west = untouched side of the 5|5 spinner counts 10, east 0 (5|0 on 5) -> 10 + 0 = 10 -> scores 10
            LessonBlock.Table(
                listOf(east(5, 5), east(5, 0)),
                "While one side of the spinner is still free, that side counts the whole double: 10 + 0 = 10. That play scores 10.",
                spinner = true,
            ),
            // ends: west 6 (5|6 on 5), east 0 -> spinner no longer counts -> 6 + 0 = 6, no score
            LessonBlock.Table(
                listOf(east(5, 5), east(5, 0), west(5, 6)),
                "Now both sides are covered and the spinner stops counting. The ends are 6 and 0: 6, no score.",
                spinner = true,
            ),
            // ends: west 6, east 0, north 1 (5|1 on 5), south 3 (5|3 on 5)
            // after north 5|1: 6 + 0 + 1 = 7, no score. after south 5|3: 6 + 0 + 1 + 3 = 10 -> scores 10
            LessonBlock.Table(
                listOf(east(5, 5), east(5, 0), west(5, 6), north(5, 1), south(5, 3)),
                "5|1 went north (6 + 0 + 1 = 7, no score). Then 5|3 went south: 6 + 0 + 1 + 3 = 10, which scores 10.",
                spinner = true,
            ),
            LessonBlock.Text(
                "North and south only count once a tile is on them. With four ends there are many more ways to reach a multiple of 5, " +
                    "so check every end before you play.",
            ),
            LessonBlock.Tip("All four branches of the spinner must match the double's number: here, a 5."),
        ),
    )

    private val scoringSetup = Lesson(
        id = "scoring-setup",
        category = SCORING,
        title = "Setting up and denying points",
        summary = "Don't hand the next player an easy score, and keep tiles that score for you.",
        practice = GameMode.ALL_FIVES,
        blocks = listOf(
            LessonBlock.Text(
                "Every play changes the count. After your move, ask: what can the next player do with these ends?",
            ),
            // opening 3|2: 3 + 2 = 5 -> scores 5. east 2|0: 3 + 0 = 3. west 3|5: 5 + 0 = 5 -> scores 5
            LessonBlock.Table(
                listOf(east(3, 2), east(2, 0), west(3, 5)),
                "You played 3|5 on the west end: 5 + 0 = 5, you score 5. But look at the ends.",
                spinner = true,
            ),
            LessonBlock.Text(
                "With a 5 and a 0 open, the next player scores 10 with 0|5 on the 0 end (5 + 5) or with 5|5 on the 5 end (10 + 0). " +
                    "Five points for you, maybe ten for them.",
            ),
            LessonBlock.Text(
                "Some tiles change the count by exactly 5: 0|5 played on a 0 (the end goes from 0 to 5), 1|6 played on a 1, " +
                    "and 5|5 played on a 5 (the end goes from 5 to 10). When the count is already a multiple of 5, they score again.",
            ),
            LessonBlock.Hand(
                listOf(t(0, 5), t(1, 6), t(5, 5)),
                "Tiles that add 5 to the count. Hold on to them until the count is right.",
            ),
            LessonBlock.Tip("If you can't score, avoid leaving 5s and 0s open: they are the ends the next player can most easily turn into points."),
        ),
    )

    private val scoringThrees = Lesson(
        id = "scoring-threes",
        category = SCORING,
        title = "All Threes",
        summary = "Like All Fives, but you score on multiples of 3 and play to 90.",
        practice = GameMode.ALL_THREES,
        blocks = listOf(
            LessonBlock.Text(
                "All Threes works exactly like All Fives, with the spinner and everything, but a play scores when the open ends add up " +
                    "to a multiple of 3. The first to 90 wins.",
            ),
            // only the opening double is down: 3 + 3 = 6 -> scores 6
            LessonBlock.Table(
                listOf(east(3, 3)),
                "The opening 3|3 counts 6. The opener scores 6 right away.",
                spinner = true,
            ),
            // ends: west = untouched side of 3|3 counts 6, east 6|6 double at the end counts 12 -> 6 + 12 = 18 -> scores 18
            LessonBlock.Table(
                listOf(east(3, 3), east(3, 6), east(6, 6)),
                "3|6 then 6|6 went east. The free side of the spinner counts 6 and 6|6 counts 12: 18, which scores 18. " +
                    "In All Fives this would score nothing.",
                spinner = true,
            ),
            // ends: west 0 (3|0 on 3), east 6|6 counts 12 -> 0 + 12 = 12 -> scores 12
            LessonBlock.Table(
                listOf(east(3, 3), east(3, 6), east(6, 6), west(3, 0)),
                "3|0 covers the west side. The spinner stops counting: 0 + 12 = 12, which scores 12.",
                spinner = true,
            ),
            LessonBlock.Text(
                "Multiples of 3 come up more often than multiples of 5, so scores are smaller but more frequent. " +
                    "At the end of a hand, leftover pips are rounded to the nearest 3: 13 becomes 12, 14 becomes 15.",
            ),
            LessonBlock.Tip("In Threes, tiles whose halves differ by 3 or 6 (0|3, 1|4, 2|5, 3|6, 0|6) keep a scoring count scoring."),
        ),
    )

    // ---------------------------------------------------------------------------------------------
    // ADVANCED
    // ---------------------------------------------------------------------------------------------

    private val advancedMultiplayer = Lesson(
        id = "advanced-multiplayer",
        category = ADVANCED,
        title = "Playing with 3 or 4 players",
        summary = "Smaller hands, more opponents, and why the next player matters most.",
        blocks = listOf(
            LessonBlock.Text(
                "With 3 or 4 players, everyone starts with 5 tiles (7 in Block). Fewer tiles means fewer options, so you will draw or pass more often.",
            ),
            LessonBlock.Hand(
                listOf(t(6, 2), t(4, 4), t(5, 1), t(3, 0), t(2, 2)),
                "A 5-tile hand. Every tile counts, and a double like 4|4 is even harder to get rid of.",
            ),
            LessonBlock.Text(
                "Watch the next player especially. Whatever you leave open, they get the first chance to use it. " +
                    "If they passed or drew on a number, leave that number open.",
            ),
            LessonBlock.Text(
                "In scoring games, the count you leave goes to the next player first. Avoid setting them up, even if a player further " +
                    "round might benefit instead.",
            ),
            LessonBlock.Text(
                "The winner of a hand scores the pips of all opponents. A hand can be worth a lot more than in a 2-player game, " +
                    "so dumping heavy tiles matters even more.",
            ),
            LessonBlock.Tip("In a 4-player game, an opponent with one tile left is everyone's problem. Block their numbers."),
        ),
    )

    private val advancedBlock = Lesson(
        id = "advanced-block",
        category = ADVANCED,
        title = "Block strategy",
        summary = "No drawing means passes are final and counting gets exact.",
        practice = GameMode.BLOCK,
        blocks = listOf(
            LessonBlock.Text(
                "In Block, nobody draws. A tile someone doesn't hold now, they will never hold this hand. " +
                    "A pass on 3 and 5 means no 3s and no 5s for the rest of the hand.",
            ),
            LessonBlock.Text(
                "With 4 players, all 28 tiles are dealt, so every tile you can't see is in an opponent's hand. Counting is exact. " +
                    "With 2 or 3 players, some tiles stay face down out of play, so a missing tile may simply be sleeping.",
            ),
            // ends: west 0 (2|0 on 2), east 6 (2|6 on 2)
            LessonBlock.Table(
                listOf(east(2, 2), east(2, 6), west(2, 0)),
                "Ends 0 and 6. Your opponent passed here: they hold no 0 and no 6, and never will this hand.",
            ),
            LessonBlock.Text(
                "Use passes to choose your plays. If you can keep a 0 or a 6 open, your opponent must pass again while you play on.",
            ),
            LessonBlock.Text(
                "Keep a variety of numbers. With no boneyard to rescue you, a one-number hand passes again and again.",
            ),
            LessonBlock.Tip("In Block, the player who keeps the most different numbers usually wins."),
        ),
    )

    private val advancedBlockOnPurpose = Lesson(
        id = "advanced-block-on-purpose",
        category = ADVANCED,
        title = "Blocking the game on purpose",
        summary = "If your pip total is lower, ending the hand by blocking can win it.",
        practice = GameMode.BLOCK,
        blocks = listOf(
            LessonBlock.Text(
                "A game is blocked when nobody can play. The lowest pip total then wins. " +
                    "So if your hand is light and your opponent's is heavy, blocking can be the winning move.",
            ),
            // east arm: 6|1 (1), 1|0 (0), 0|6 (6), 6|3 (3), 3|4 (4), 4|6 (6) -> east end 6
            // west arm: 6|2 (2), 2|5 (5), 5|6 (6) -> west end 6
            // sixes on the table: 6|6, 6|1, 6|0, 6|3, 6|4, 6|2, 6|5 = all seven -> blocked
            LessonBlock.Table(
                listOf(
                    east(6, 6), east(6, 1), east(1, 0), east(0, 6), east(6, 3), east(3, 4), east(4, 6),
                    west(6, 2), west(2, 5), west(5, 6),
                ),
                "Both ends show 6 and all seven 6s are on the table. Nobody can play: the game is blocked.",
            ),
            LessonBlock.Text(
                "Say the last play, 4|6, was yours and you are left with 1|1 (2 pips). Unless your opponent holds 2 pips or less, " +
                    "you win the hand and score everything they hold.",
            ),
            LessonBlock.Text(
                "Before you block, compare: count your own pips, then estimate theirs from the number of tiles they hold. " +
                    "Three tiles rarely add up to less than 6 pips. Equal totals mean nobody scores.",
            ),
            LessonBlock.Text(
                "In Draw, a game can only block once the boneyard is empty, so this is mostly a Block tactic.",
            ),
            LessonBlock.Tip("Count to 7: when six tiles of a number are down and you hold the seventh, you decide when that end closes."),
        ),
    )

    // ---------------------------------------------------------------------------------------------
    // THE FOUR GAMES
    // ---------------------------------------------------------------------------------------------

    private val fourGames = Lesson(
        id = "four-games",
        category = SCORING,
        title = "The four games and their targets",
        summary = "Draw, Block, All Fives and All Threes side by side, and how to win each.",
        practice = GameMode.ALL_FIVES,
        blocks = listOf(
            LessonBlock.Text(
                "This app has four domino games: Draw, Block, All Fives and All Threes, each with a usual target as the default. " +
                    "You can pick another target, or any score you like, before a game.",
            ),
            LessonBlock.Text(
                "Draw: first to 60, scored only at the end of each round. " +
                    "Block: the same, but you pass instead of drawing. All Fives: first to 100, you score during play " +
                    "when the open ends make a multiple of 5, and round-end points are rounded to the nearest 5. " +
                    "All Threes: first to 90, multiples of 3, rounded to the nearest 3.",
            ),
            LessonBlock.Text(
                "In all four, the highest double opens, doubles are placed crosswise, and in Fives and Threes the first " +
                    "double is a spinner that branches in four directions once both its sides are played.",
            ),
            // ends: west = untouched side of the 5|5 spinner counts 10, east 0 (5|0 on 5) -> 10 + 0 = 10 -> scores 10
            LessonBlock.Table(
                listOf(east(5, 5), east(5, 0)),
                "An All Fives classic: 5|0 next to an opening 5|5. The free side of the spinner counts 10, plus 0: 10 points.",
                spinner = true,
            ),
            LessonBlock.Text(
                "Tips for every table: count your pips and dump heavy tiles early. Play doubles before they get stranded. " +
                    "Remember what your opponents drew or passed on. In Fives and Threes, look for a scoring play first, " +
                    "and don't leave an easy count for the next player.",
            ),
            LessonBlock.Tip("Practise on Hard: that computer player counts tiles and remembers your passes, so it punishes careless play."),
        ),
    )

    val all: List<Lesson> = listOf(
        basicsTiles,
        basicsDraw,
        basicsBlock,
        basicsTable,
        basicsScoring,
        strategyPips,
        strategyDoubles,
        strategyOptions,
        strategyControl,
        strategyRead,
        strategyCounting,
        strategyEndgame,
        scoringFives,
        scoringSpinner,
        scoringSetup,
        scoringThrees,
        advancedMultiplayer,
        advancedBlock,
        advancedBlockOnPurpose,
        fourGames,
    )

    fun byId(id: String): Lesson? = all.firstOrNull { it.id == id }

    /** Short one-line tips for a "tip of the day". */
    val tips: List<String> = listOf(
        "Each number appears on exactly 7 tiles. Count them and you know what's left.",
        "Play heavy tiles early. They're the points you give away if you lose the hand.",
        "Doubles only fit one number. Play them before that number runs out.",
        "Before you play, check what you can play next turn.",
        "Hold many tiles of one number? Keep that number open.",
        "An opponent who draws or passes has none of the open numbers. Remember them.",
        "In Block, a pass is final: that player won't get those numbers this hand.",
        "One tile left in an opponent's hand? Block the numbers they passed on.",
        "Losing the race? Dump your heaviest tile to limit the damage.",
        "In All Fives, look for a scoring play before anything else.",
        "A double at the end of an arm counts both halves.",
        "The free side of the spinner counts the whole double.",
        "North and south of the spinner only count once a tile is on them.",
        "Don't leave a 5 and a 0 open in All Fives: 0|5 or 5|5 scores 10 for the next player.",
        "In All Fives, 0|5, 1|6 and 5|5 can add exactly 5 to the count. Save them for a scoring board.",
        "In All Threes, tiles whose halves differ by 3, like 1|4 or 2|5, add or remove exactly 3.",
        "End-of-hand points round to the nearest 5 in Fives and the nearest 3 in Threes.",
        "In Fives and Threes, the match ends the moment someone reaches the target.",
        "With 3 or 4 players, watch the next player most closely.",
        "Blocking a game wins only if your pip total is lowest. Count first.",
        "Ties in a blocked game score nothing for anyone.",
        "Keep tiles of different numbers. Variety means fewer draws and passes.",
    )
}
