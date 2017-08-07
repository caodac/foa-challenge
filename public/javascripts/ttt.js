/**
 * Created by southalln on 8/3/17.
 */
var EMPTY = "&nbsp;",
    board = "",
    boxes = [],
    turn = "X",
    realPlayers = "O|X",
    score = 0,
    sleepCt = 1000;

/*
 * Initializes the Tic Tac Toe board and starts the game.
 */
function init() {
    document.getElementById("ttt-board").innerHTML = "<div class=\"vt100\">ONE OR TWO PLAYERS?<br>PLEASE LIST NUMBER<br>OF PLAYERS:<form id=\"numPlayersForm\" action=\"javascript:;\"><input type=\"text\" id=\"numPlayers\" style=\"background: black; border: none; color: white;\"></form></div>";
    var textbox = document.getElementById('numPlayers');
    textbox.focus();
    $('form').on('submit', runGame);
}

function runGame() {
    var numPlayers = document.getElementById('numPlayers').value;
    console.log(numPlayers);
    if (numPlayers == "1" || numPlayers.toUpperCase() == "ONE")
        realPlayers = "O";
    else if (numPlayers == "0" || numPlayers.toUpperCase() == "ZERO")
        realPlayers = "";
    console.log(realPlayers);
    document.getElementById("ttt-board").innerHTML = "";
    var boardSrc = document.getElementById("board-image").src;
    var boardImg = document.createElement('div');
    boardImg.innerHTML = "<img src=\""+boardSrc+"\" class=\"backgrd\">";
    document.getElementById("ttt-board").appendChild(boardImg);
    var overlay = document.createElement('div');
    overlay.className = 'overlay';
    var tttdiv = document.createElement('div');
    tttdiv.id = 'tictactoe';
    overlay.appendChild(tttdiv);
    document.getElementById("ttt-board").appendChild(overlay);

    var boardTbl = document.createElement('table');
    boardTbl.setAttribute("border", 0);
    boardTbl.setAttribute("cellspacing", 0);

    var identifier = 0;
    for (var i = 0; i < 3; i++) {
        var row = document.createElement('tr');
        boardTbl.appendChild(row);
        for (var j = 0; j < 3; j++) {
            var cell = document.createElement('td');
            cell.setAttribute('height', 120);
            cell.setAttribute('width', 120);
            cell.setAttribute('align', 'center');
            cell.setAttribute('valign', 'center');
            cell.classList.add('cell' + (3*i+j));
            cell.classList.add('col' + j,'row' + i);
            if (i == j) {
                cell.classList.add('diagonal0');
            }
            if (j == 3 - i - 1) {
                cell.classList.add('diagonal1');
            }
            cell.identifier = identifier;
            cell.addEventListener("makeMark", setMark);
            cell.addEventListener("click", clickSet);
            row.appendChild(cell);
            boxes.push(cell);
            identifier += 1;
        }
    }

    document.getElementById("tictactoe").appendChild(boardTbl);
    startNewGame();
    //runScript();
}

/*
 * New game
 */
function startNewGame() {
    boxes.forEach(function (square) {
        square.innerHTML = EMPTY;
    });
    board = "---------";
    if (realPlayers.indexOf(turn) == -1) {
        autoSet();
    }
}

function isWinner(bo, le) {
    // Given a board and a player's letter, this function returns true if that player has won.
    return ((bo.charAt(6) == le && bo.charAt(7) == le && bo.charAt(8) == le) || //across the top
    (bo.charAt(3) == le && bo.charAt(4) == le && bo.charAt(5) == le) || //across the middle
    (bo.charAt(0) == le && bo.charAt(1) == le && bo.charAt(2) == le) || //across the bottom
    (bo.charAt(6) == le && bo.charAt(3) == le && bo.charAt(0) == le) || //down the left side
    (bo.charAt(7) == le && bo.charAt(4) == le && bo.charAt(1) == le) || //down the middle
    (bo.charAt(8) == le && bo.charAt(5) == le && bo.charAt(2) == le) || //down the right side
    (bo.charAt(6) == le && bo.charAt(4) == le && bo.charAt(2) == le) || //diagonal
    (bo.charAt(8) == le && bo.charAt(4) == le && bo.charAt(0) == le)); //diagonal
}

function chooseMark() {
    // Here is our algorithm for our Tic Tac Toe AI:
    // First, check if we can win in the next move
    for (var cell=0; cell<9; cell++) {
        if (board.charAt(cell) == '-') {
            var copy = board.substring(0, cell) + turn + board.substring(cell+1, 9);
            if (isWinner(copy, turn)) {
                return cell;
            }
        }
    }

    // Check if the player could win on his next move, and block them.
    var notPlayer = 'O';
    if (turn == notPlayer) notPlayer = 'X';
    for (var cell=0; cell<9; cell++) {
        if (board.charAt(cell) == '-') {
            var copy = board.substring(0, cell) + notPlayer + board.substring(cell+1, 9);
            if (isWinner(copy, notPlayer)) {
                return cell;
            }
        }
    }

    // Protect from double jeopardy
    var dj = [0,1,3,2,1,5,6,3,7,8,5,7];
    for (var i=0; i<3; i++) {
        if (board.charAt(dj[i * 3]) == '-' && board.charAt(dj[i * 3 + 1]) == board.charAt(dj[i * 3 + 2]) && board.charAt(dj[i * 3 + 1]) != '-') {
            // double jeopardy!", dj[i*3]
            return dj[i * 3];
        }
    }

    // Try to take the center, if it is free.
    if (board.charAt(4)=='-')
        return 4;

    // Try to take one of the corners, if they are free.
    var list = [0, 2, 6, 8];
    while (list.length > 0) {
        var random = Math.floor(Math.random() * list.length);
        if (board.charAt(list[random]) == '-') {
            return list[random];
        }
        list.splice(random, 1);
    }

    // Move on one of the sides.
    var list = [1, 3, 5, 7];
    while (list.length > 0) {
        var random = Math.floor(Math.random() * list.length);
        if (board.charAt(list[random]) == '-') {
            return list[random];
        }
        list.splice(random, 1);
    }

    return 0;
}


function setMarks(setBoard) {
    for (i=0; i<setBoard.length; i++) {
        if (setBoard.charAt(i) != '-') {
            turn = setBoard.charAt(i);
            boxes[i].dispatchEvent(new Event("makeMark"));
        }
    }
}

function autoSet() {
    var newMark = chooseMark();
    boxes[newMark].dispatchEvent(new Event("makeMark"));
}

function clickSet() {
    if (realPlayers.indexOf(turn)>-1) {
        this.dispatchEvent(new Event("makeMark"));
    }
}

/*
 * Sets clicked square and also updates the turn.
 */
async function setMark() {
    if (this.innerHTML !== EMPTY) {
        return;
    }
    var mark = turn === "X" ? "cross" : "circle";
    var markSrc = document.getElementById(mark+"-image").src;
    this.innerHTML = "<img src=\""+markSrc+"\" class=\""+mark+" mark\">";
    var cell = this.identifier;
    board = board.substring(0, cell) + turn + board.substring(cell+1, 9);
    await sleep(sleepCt);
    sleepCt = sleepCt - 35;
    if (sleepCt < 35) sleepCt = 35;
    if (isWinner(board, turn)) {
        setTimeout(function(){
            //alert('Winner: Player ' + turn);
            score = 0;
            startNewGame();
        }, 500);
    } else if (board.indexOf("-") == -1) {
        turn = turn === "X" ? "O" : "X";
        setTimeout(function(){
            //alert("Draw");
            score += 1;
            if (score < 6)
                startNewGame();
            else {
                var action = window.location.href+"233";
                document.getElementById("ttt-board").innerHTML = "<div class=\"vt100\">A STRANGE GAME. <br>THE ONLY WINNING MOVE IS NOT TO PLAY.</div><div class=\"vt100\"><br>How about a nice game of something else?</div><div><form action=\""+action+"\"><input type=\"submit\" value=\"Continue in challenge\" /></form></div>";
            }
        }, 500);
    } else {
        turn = turn === "X" ? "O" : "X";
        document.getElementById('turn').textContent = 'Player ' + turn;
        if (realPlayers.indexOf(turn) == -1) {
            autoSet();
        }
    }
}

function sleep(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
}

async function runScript() {
    var moveSet = "----X-------OX-------OX--X--O-OX--X-XO-OX--X-XO-OX--XOXO-OXX-XOXO-OXXOXOXOXOXXOXO----O-------XO-----O-XO-----O-XO--X--O-XO-OX--OXXO-OX--OXXOOOX-XOXXOOOX-XOXXOOOXO----X-------OX-------OXX------OXX-O----OXXXO---OOXXXO--XOOXXXO-OXOOXXXO-OXOOXXXOX----O--------O--X-----OO-X----XOO-X----XOOOX---XXOOOX--OXXOOOX--OXXOOOXXOOXXOOOXX----X--------XO------XXO------XXO-O--X-XXO-O-OX-XXO-O-OX-XXO-OXOX-XXOOOXOXXXXOOOX----O-------XO-------XOO------XOO-X----XOOOX---XXOOOX--OXXOOOX--OXXOOOXXOOXXOOOXX----X--------XO-------XO-X--O--XO-X--OX-XO-X--OX-XOOX--OXXXOOX--OXXXOOXOXOXXXOOXO----O-------XO-------XO--O--X-XO--O-OX-XO--O-OX-XO--OXOX-XOO-OXOXXXOO-OXOXXXOOOOX----X--------X--O--X--X--O--X-OX--O--X-OX-XO--XOOX-XO--XOOXXXO--XOOXXXOOXXOOXXXOO----O--------O--X-----OO-X----XOO-X----XOOOX---XXOOOX--OXXOOOX-XOXXOOOX-XOXXOOOXO----X--------XO-------XO-X--O--XO-X--OX-XO-X--OX-XOOX--OXXXOOX--OXXXOOXOXOXXXOOXO----O-------XO-------XO--O--X-XO--O-OX-XO--O-OX-XO--OXOX-XOO-OXOXXXOO-OXOXXXOOOOX----X--------X--O----XX--O----XXO-O--X-XXO-O-OX-XXO-O-OXXXXO-O-OXXXXOOO-OXXXXOOOX----O-----X--O-----X-OO-----X-OOX----XOOOX----XOOOXX---XOOOXXO--XOOOXXOXOXOOOXXOX----X-----O--X-----O-XX-----O-XXO----OXXXO----OXXXOO---OXXXOOX--OXXXOOXOXOXXXOOXO----O-------XO-------XOO------XOO-X----XOOOX---XXOOOX--OXXOOOX-XOXXOOOX-XOXXOOOXO";
    var val = $.trim($("textarea").val());
    if (val != "") {
        moveSet = val;
    }
    var sleepCt = 1000;
    for (var i=0; i<moveSet.length; i=i+9) {
        move = moveSet.substring(i, i+9);
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
    //runScript();
});
