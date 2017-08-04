/**
 * Created by southalln on 8/3/17.
 */
/*
 * Tic Tac Toe
 *
 * A Tic Tac Toe game in HTML/JavaScript/CSS.
 *
 * @author: Vasanth Krishnamoorthy
 */
var N_SIZE = 3,
    EMPTY = "&nbsp;",
    boxes = [],
    turn = "X",
    score,
    moves;

/*
 * Initializes the Tic Tac Toe board and starts the game.
 */
function init() {
    var board = document.createElement('table');
    board.setAttribute("border", 0);
    board.setAttribute("cellspacing", 0);

    var identifier = 1;
    for (var i = 0; i < N_SIZE; i++) {
        var row = document.createElement('tr');
        board.appendChild(row);
        for (var j = 0; j < N_SIZE; j++) {
            var cell = document.createElement('td');
            cell.setAttribute('height', 120);
            cell.setAttribute('width', 120);
            cell.setAttribute('align', 'center');
            cell.setAttribute('valign', 'center');
            cell.classList.add('col' + j,'row' + i);
            if (i == j) {
                cell.classList.add('diagonal0');
            }
            if (j == N_SIZE - i - 1) {
                cell.classList.add('diagonal1');
            }
            cell.identifier = identifier;
            cell.addEventListener("click", set);
            row.appendChild(cell);
            boxes.push(cell);
            identifier += identifier;
        }
    }

    document.getElementById("tictactoe").appendChild(board);
    startNewGame();

}

/*
 * New game
 */
function startNewGame() {
    score = {
        "X": 0,
        "O": 0
    };
    moves = 0;
    turn = "X";
    boxes.forEach(function (square) {
        square.innerHTML = EMPTY;
    });
}

/*
 * Check if a win or not
 */
function win(clicked) {
    // Get all cell classes
    var memberOf = clicked.className.split(/\s+/);
    for (var i = 0; i < memberOf.length; i++) {
        var testClass = '.' + memberOf[i];
        var items = contains('#tictactoe ' + testClass, turn === "X" ? "cross" : "circle");
        // winning condition: turn == N_SIZE
        if (items.length == N_SIZE) {
            return true;
        }
    }
    return false;
}

function contains(selector, text) {
    var elements = document.querySelectorAll(selector);
    return [].filter.call(elements, function(element){
        return RegExp(text).test(element.firstChild.className);
    });
}

function setMark(mark) {
    boxes[mark].dispatchEvent(new Event("click"));
}

function setMarks(board) {
    for (i=0; i<board.length; i++) {
        if (board.charAt(i) != '-') {
            turn = board.charAt(i);
            boxes[i].dispatchEvent(new Event("click"));
        }
    }
}

/*
 * Sets clicked square and also updates the turn.
 */
function set() {
    if (this.innerHTML !== EMPTY) {
        return;
    }
    var mark = turn === "X" ? "cross" : "circle";
    this.innerHTML = "<img src=\"http://localhost:9000/challenge/assets/images/"+mark+".png\" class=\""+mark+" mark\">";
    moves += 1;
    score[turn] += this.identifier;
    if (win(this)) {
        //alert('Winner: Player ' + turn);
        setTimeout(function(){
            startNewGame();
        }, 500);
    } else if (moves === N_SIZE * N_SIZE) {
        //alert("Draw");
        setTimeout(function(){
            startNewGame();
        }, 500);
    } else {
        turn = turn === "X" ? "O" : "X";
        document.getElementById('turn').textContent = 'Player ' + turn;
    }
}

function sleep(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
}

async function runScript() {
    var moves = "----X-------OX-------OX--X--O-OX--X-XO-OX--X-XO-OX--XOXO-OXX-XOXO-OXXOXOXOXOXXOXO----O-------XO-----O-XO-----O-XO--X--O-XO-OX--OXXO-OX--OXXOOOX-XOXXOOOX-XOXXOOOXO----X-------OX-------OXX------OXX-O----OXXXO---OOXXXO--XOOXXXO-OXOOXXXO-OXOOXXXOX----O--------O--X-----OO-X----XOO-X----XOOOX---XXOOOX--OXXOOOX--OXXOOOXXOOXXOOOXX----X--------XO------XXO------XXO-O--X-XXO-O-OX-XXO-O-OX-XXO-OXOX-XXOOOXOXXXXOOOX----O-------XO-------XOO------XOO-X----XOOOX---XXOOOX--OXXOOOX--OXXOOOXXOOXXOOOXX----X--------XO-------XO-X--O--XO-X--OX-XO-X--OX-XOOX--OXXXOOX--OXXXOOXOXOXXXOOXO----O-------XO-------XO--O--X-XO--O-OX-XO--O-OX-XO--OXOX-XOO-OXOXXXOO-OXOXXXOOOOX----X--------X--O--X--X--O--X-OX--O--X-OX-XO--XOOX-XO--XOOXXXO--XOOXXXOOXXOOXXXOO----O--------O--X-----OO-X----XOO-X----XOOOX---XXOOOX--OXXOOOX-XOXXOOOX-XOXXOOOXO----X--------XO-------XO-X--O--XO-X--OX-XO-X--OX-XOOX--OXXXOOX--OXXXOOXOXOXXXOOXO----O-------XO-------XO--O--X-XO--O-OX-XO--O-OX-XO--OXOX-XOO-OXOXXXOO-OXOXXXOOOOX----X--------X--O----XX--O----XXO-O--X-XXO-O-OX-XXO-O-OXXXXO-O-OXXXXOOO-OXXXXOOOX----O-----X--O-----X-OO-----X-OOX----XOOOX----XOOOXX---XOOOXXO--XOOOXXOXOXOOOXXOX----X-----O--X-----O-XX-----O-XXO----OXXXO----OXXXOO---OXXXOOX--OXXXOOXOXOXXXOOXO----O-------XO-------XOO------XOO-X----XOOOX---XXOOOX--OXXOOOX-XOXXOOOX-XOXXOOOXO";
    var val = $.trim($("textarea").val());
    if (val != "") {
        moves = val;
    }
    var sleepCt = 1000;
    for (var i=0; i<moves.length; i=i+9) {
        move = moves.substring(i, i+9);
        //console.log(i+":"+move);
        setMarks(move);
        await sleep(sleepCt);
        sleepCt = sleepCt - 35;
        if (sleepCt < 35) sleepCt = 35;
    }
    var action = window.location.href+"233";
    document.getElementById("ttt-board").innerHTML = "<div class=\"vt100\">A STRANGE GAME. <br>THE ONLY WINNING MOVE IS NOT TO PLAY.</div><div class=\"vt100\"><br>How about a nice game of something else?</div><div><form action=\""+action+"\"><input type=\"submit\" value=\"Continue in challenge\" /></form></div>";
}

// Add event listeners once the DOM has fully loaded by listening for the
// `DOMContentLoaded` event on the document, and adding your listeners to
// specific elements when it triggers.
document.addEventListener('DOMContentLoaded', function () {
    init();
    runScript();
});
