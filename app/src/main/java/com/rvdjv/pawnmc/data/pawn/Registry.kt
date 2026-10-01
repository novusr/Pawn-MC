package com.rvdjv.pawnmc.data.pawn

enum class PawnItemKind {
    KEYWORD,
    DIRECTIVE,
    TYPE,
    CONSTANT,
    FUNCTION,
    CALLBACK,
    OPERATOR
}

data class _item(
    val name: String,
    val description: String,
    val kind: PawnItemKind
)

/**
 * Dedicated Pawn language registry holding official Pawn specifications,
 * keywords, preprocessor directives, types, constants, operators, SA-MP
 * callbacks and native functions. Stored in the data layer separately from
 * the UI, and used by both the syntax highlighter and the autocompletion
 * popup so the two can never drift apart.
 */
object PawnRegistry {

    val DIRECTIVES: List<_item> = listOf(
        _item("#include", "Include an external source or header file", PawnItemKind.DIRECTIVE),
        _item("#define", "Define a preprocessor macro or constant", PawnItemKind.DIRECTIVE),
        _item("#undef", "Undefine an existing macro", PawnItemKind.DIRECTIVE),
        _item("#if", "Conditional compilation check", PawnItemKind.DIRECTIVE),
        _item("#elseif", "Alternative conditional compilation check", PawnItemKind.DIRECTIVE),
        _item("#else", "Default conditional compilation branch", PawnItemKind.DIRECTIVE),
        _item("#endif", "End of conditional compilation block", PawnItemKind.DIRECTIVE),
        _item("#endinput", "Stops reading the current file", PawnItemKind.DIRECTIVE),
        _item("#tryinclude", "Include a file only if it has not been included yet", PawnItemKind.DIRECTIVE),
        _item("#endscript", "Stop reading the current file without an error", PawnItemKind.DIRECTIVE),
        _item("#file", "Set the file name reported by diagnostics", PawnItemKind.DIRECTIVE),
        _item("#warning", "Enable, disable or raise a compiler warning", PawnItemKind.DIRECTIVE),
        _item("#assert", "Compile-time assertion check", PawnItemKind.DIRECTIVE),
        _item("#error", "Emit user-defined compilation error", PawnItemKind.DIRECTIVE),
        _item("#pragma", "Compiler control directive", PawnItemKind.DIRECTIVE),
        _item("#emit", "Emit raw AMX bytecode opcode", PawnItemKind.DIRECTIVE),
        _item("#line", "Set line number and optional filename", PawnItemKind.DIRECTIVE),
        _item("tabsize", "Pragma to set tab indent width", PawnItemKind.DIRECTIVE),
        _item("dynamic", "Pragma to set AMX stack/heap memory size", PawnItemKind.DIRECTIVE),
        _item("ctrlchar", "Pragma to change escape character", PawnItemKind.DIRECTIVE),
        _item("deprecated", "Pragma to mark symbol as deprecated", PawnItemKind.DIRECTIVE),
        _item("semicolon", "Pragma to enforce or relax semicolons", PawnItemKind.DIRECTIVE)
    )

    val KEYWORDS: List<_item> = listOf(
        _item("stock", "Declares variable or function compiled only if used", PawnItemKind.KEYWORD),
        _item("public", "Declares an exported function callable by AMX", PawnItemKind.KEYWORD),
        _item("forward", "Forward declaration for public functions", PawnItemKind.KEYWORD),
        _item("hook", "Hooks a callback or function so multiple handlers can be chained", PawnItemKind.KEYWORD),
        _item("native", "Declares a C/C++ native plugin or host function", PawnItemKind.KEYWORD),
        _item("new", "Declares a local or global variable", PawnItemKind.KEYWORD),
        _item("static", "Declares static scoped variable or private function", PawnItemKind.KEYWORD),
        _item("const", "Declares an immutable constant variable", PawnItemKind.KEYWORD),
        _item("enum", "Defines an enumerated constant structure", PawnItemKind.KEYWORD),
        _item("state", "State machine transition declaration", PawnItemKind.KEYWORD),
        _item("goto", "Jump unconditionally to label", PawnItemKind.KEYWORD),
        _item("break", "Break out of loop or switch block", PawnItemKind.KEYWORD),
        _item("continue", "Skip to next iteration of loop", PawnItemKind.KEYWORD),
        _item("return", "Return value and exit function", PawnItemKind.KEYWORD),
        _item("if", "Conditional branch statement", PawnItemKind.KEYWORD),
        _item("else", "Alternative conditional branch", PawnItemKind.KEYWORD),
        _item("while", "Loop while condition is true", PawnItemKind.KEYWORD),
        _item("do", "Loop executing at least once", PawnItemKind.KEYWORD),
        _item("for", "Standard for-loop statement", PawnItemKind.KEYWORD),
        _item("switch", "Multi-branch switch statement", PawnItemKind.KEYWORD),
        _item("case", "Case label in switch statement", PawnItemKind.KEYWORD),
        _item("default", "Default branch in switch statement", PawnItemKind.KEYWORD),
        _item("sizeof", "Returns size in cells or array dimensions", PawnItemKind.KEYWORD),
        _item("tagof", "Returns tag integer representing data type", PawnItemKind.KEYWORD),
        _item("assert", "Runtime assertion condition", PawnItemKind.KEYWORD),
        _item("sleep", "Pawn abstract machine sleep call", PawnItemKind.KEYWORD),
        _item("char", "Sub-cell character packing indexer", PawnItemKind.KEYWORD),
        _item("defined", "Preprocessor check for a defined macro", PawnItemKind.KEYWORD),
        _item("emit", "Inline AMX bytecode opcode", PawnItemKind.KEYWORD),
        _item("__emit", "Argument list marker for a raw EMIT", PawnItemKind.KEYWORD),
        _item("exit", "Terminate the script immediately", PawnItemKind.KEYWORD),
        _item("operator", "User defined operator overload declaration", PawnItemKind.KEYWORD),
        _item("*begin", "Start of a multi-line block comment", PawnItemKind.KEYWORD),
        _item("*end", "End of a multi-line block comment", PawnItemKind.KEYWORD),
        _item("*then", "Block comment continuation of the previous line", PawnItemKind.KEYWORD)
    )

    /**
     * Multi-character and single-character Pawn operators, ordered so that
     * longer sequences are matched before their own prefixes.
     *
     * The same list drives syntax highlighting (the lexer greedily consumes the
     * longest entry at the cursor) and the autocompletion popup, so both stay
     * consistent by construction.
     */
    val OPERATORS: List<_item> = listOf(
        _item(">>>=", "Unsigned right shift assignment", PawnItemKind.OPERATOR),
        _item("<<=", "Left shift assignment", PawnItemKind.OPERATOR),
        _item(">>=", "Arithmetic right shift assignment", PawnItemKind.OPERATOR),
        _item("*=", "Multiplication assignment", PawnItemKind.OPERATOR),
        _item("/=", "Division assignment", PawnItemKind.OPERATOR),
        _item("%=", "Modulo assignment", PawnItemKind.OPERATOR),
        _item("+=", "Addition assignment", PawnItemKind.OPERATOR),
        _item("-=", "Subtraction assignment", PawnItemKind.OPERATOR),
        _item("&=", "Bitwise AND assignment", PawnItemKind.OPERATOR),
        _item("^=", "Bitwise XOR assignment", PawnItemKind.OPERATOR),
        _item("|=", "Bitwise OR assignment", PawnItemKind.OPERATOR),
        _item(">>>", "Unsigned right shift", PawnItemKind.OPERATOR),
        _item(">>", "Arithmetic right shift", PawnItemKind.OPERATOR),
        _item("<<", "Left shift", PawnItemKind.OPERATOR),
        _item("||", "Logical OR", PawnItemKind.OPERATOR),
        _item("&&", "Logical AND", PawnItemKind.OPERATOR),
        _item("==", "Equality comparison", PawnItemKind.OPERATOR),
        _item("!=", "Inequality comparison", PawnItemKind.OPERATOR),
        _item("<=", "Less than or equal comparison", PawnItemKind.OPERATOR),
        _item(">=", "Greater than or equal comparison", PawnItemKind.OPERATOR),
        _item("++", "Pre/post increment", PawnItemKind.OPERATOR),
        _item("--", "Pre/post decrement", PawnItemKind.OPERATOR),
        _item("...", "Ellipsis / argument spread", PawnItemKind.OPERATOR),
        _item("..", "Argument range marker", PawnItemKind.OPERATOR),
        _item("::", "Tag scope separator", PawnItemKind.OPERATOR),
        _item("+", "Addition", PawnItemKind.OPERATOR),
        _item("-", "Subtraction or negation", PawnItemKind.OPERATOR),
        _item("*", "Multiplication", PawnItemKind.OPERATOR),
        _item("/", "Division", PawnItemKind.OPERATOR),
        _item("%", "Modulo", PawnItemKind.OPERATOR),
        _item("=", "Assignment", PawnItemKind.OPERATOR),
        _item("!", "Logical NOT", PawnItemKind.OPERATOR),
        _item("<", "Less than comparison", PawnItemKind.OPERATOR),
        _item(">", "Greater than comparison", PawnItemKind.OPERATOR),
        _item("&", "Bitwise AND", PawnItemKind.OPERATOR),
        _item("|", "Bitwise OR", PawnItemKind.OPERATOR),
        _item("^", "Bitwise XOR", PawnItemKind.OPERATOR),
        _item("~", "Bitwise NOT", PawnItemKind.OPERATOR),
        _item("?", "Ternary conditional marker", PawnItemKind.OPERATOR),
        _item(":", "Ternary else marker", PawnItemKind.OPERATOR),
        _item(".", "Array member or argument range separator", PawnItemKind.OPERATOR),
        _item(",", "Argument separator", PawnItemKind.OPERATOR),
        _item(";", "Statement terminator", PawnItemKind.OPERATOR)
    )

    val TYPES: List<_item> = listOf(
        _item("Float:", "Floating point single-precision tag", PawnItemKind.TYPE),
        _item("bool:", "Boolean true/false data tag", PawnItemKind.TYPE),
        _item("File:", "File handle tag for file operations", PawnItemKind.TYPE),
        _item("Text:", "Global text draw identifier tag", PawnItemKind.TYPE),
        _item("PlayerText:", "Player-specific text draw identifier tag", PawnItemKind.TYPE),
        _item("Menu:", "Game menu identifier tag", PawnItemKind.TYPE),
        _item("Text3D:", "Global 3D text label identifier tag", PawnItemKind.TYPE),
        _item("PlayerText3D:", "Player-specific 3D text label tag", PawnItemKind.TYPE)
    )

    val CONSTANTS: List<_item> = listOf(
        _item("true", "Boolean true literal (1)", PawnItemKind.CONSTANT),
        _item("false", "Boolean false literal (0)", PawnItemKind.CONSTANT),
        _item("cellmax", "Maximum signed 32-bit cell value", PawnItemKind.CONSTANT),
        _item("cellmin", "Minimum signed 32-bit cell value", PawnItemKind.CONSTANT),
        _item("INVALID_PLAYER_ID", "Special ID representing no valid player (65535)", PawnItemKind.CONSTANT),
        _item("INVALID_VEHICLE_ID", "Special ID representing no valid vehicle (65535)", PawnItemKind.CONSTANT),
        _item("INVALID_OBJECT_ID", "Special ID representing no valid object (65535)", PawnItemKind.CONSTANT),
        _item("INVALID_TIMER_ID", "Invalid timer handle representation", PawnItemKind.CONSTANT),
        _item("MAX_PLAYERS", "Maximum player slots on server (default 50/500/1000)", PawnItemKind.CONSTANT),
        _item("MAX_VEHICLES", "Maximum vehicle slots on server (default 2000)", PawnItemKind.CONSTANT),
        _item("MAX_OBJECTS", "Maximum global object limit (1000)", PawnItemKind.CONSTANT),
        _item("MAX_ACTORS", "Maximum static actors on server (1000)", PawnItemKind.CONSTANT),
        _item("MAX_TEXT_DRAWS", "Maximum global text draws (2048)", PawnItemKind.CONSTANT),
        _item("MAX_MENUS", "Maximum menu instances (128)", PawnItemKind.CONSTANT),
        _item("MAX_GANG_ZONES", "Maximum gang zones (1024)", PawnItemKind.CONSTANT),
        _item("SPECIAL_ACTION_NONE", "Player special action: None", PawnItemKind.CONSTANT),
        _item("SPECIAL_ACTION_DUCK", "Player special action: Ducking", PawnItemKind.CONSTANT),
        _item("SPECIAL_ACTION_USEJETPACK", "Player special action: Jetpack", PawnItemKind.CONSTANT),
        _item("PLAYER_STATE_NONE", "Player state: None", PawnItemKind.CONSTANT),
        _item("PLAYER_STATE_ONFOOT", "Player state: On Foot", PawnItemKind.CONSTANT),
        _item("PLAYER_STATE_DRIVER", "Player state: Driver of a vehicle", PawnItemKind.CONSTANT),
        _item("PLAYER_STATE_PASSENGER", "Player state: Passenger in a vehicle", PawnItemKind.CONSTANT),
        _item("PLAYER_STATE_WASTED", "Player state: Dead/Wasted", PawnItemKind.CONSTANT),
        _item("PLAYER_STATE_SPAWNED", "Player state: Spawned", PawnItemKind.CONSTANT),
        _item("PLAYER_STATE_SPECTATING", "Player state: Spectating", PawnItemKind.CONSTANT)
    )

    val CALLBACKS: List<_item> = listOf(
        _item("OnGameModeInit", "Callback triggered when the gamemode loads", PawnItemKind.CALLBACK),
        _item("OnGameModeExit", "Callback triggered when the gamemode shuts down", PawnItemKind.CALLBACK),
        _item("OnFilterScriptInit", "Callback triggered when a filterscript loads", PawnItemKind.CALLBACK),
        _item("OnFilterScriptExit", "Callback triggered when a filterscript unloads", PawnItemKind.CALLBACK),
        _item("OnPlayerConnect", "Callback triggered when a player joins the server", PawnItemKind.CALLBACK),
        _item("OnPlayerDisconnect", "Callback triggered when a player leaves the server", PawnItemKind.CALLBACK),
        _item("OnPlayerSpawn", "Callback triggered when a player spawns in the world", PawnItemKind.CALLBACK),
        _item("OnPlayerDeath", "Callback triggered when a player dies or is killed", PawnItemKind.CALLBACK),
        _item("OnVehicleSpawn", "Callback triggered when a vehicle spawns", PawnItemKind.CALLBACK),
        _item("OnVehicleDeath", "Callback triggered when a vehicle is destroyed", PawnItemKind.CALLBACK),
        _item("OnPlayerText", "Callback triggered when a player sends public chat", PawnItemKind.CALLBACK),
        _item("OnPlayerCommandText", "Callback triggered when a player enters a slash command", PawnItemKind.CALLBACK),
        _item("OnPlayerRequestClass", "Callback triggered when a player views class selection", PawnItemKind.CALLBACK),
        _item("OnPlayerEnterVehicle", "Callback triggered when a player starts entering a vehicle", PawnItemKind.CALLBACK),
        _item("OnPlayerExitVehicle", "Callback triggered when a player starts exiting a vehicle", PawnItemKind.CALLBACK),
        _item("OnPlayerStateChange", "Callback triggered when a player changes state (foot, car, etc.)", PawnItemKind.CALLBACK),
        _item("OnPlayerEnterCheckpoint", "Callback triggered when a player enters standard checkpoint", PawnItemKind.CALLBACK),
        _item("OnPlayerLeaveCheckpoint", "Callback triggered when a player leaves standard checkpoint", PawnItemKind.CALLBACK),
        _item("OnPlayerEnterRaceCheckpoint", "Callback triggered when entering race checkpoint", PawnItemKind.CALLBACK),
        _item("OnPlayerLeaveRaceCheckpoint", "Callback triggered when leaving race checkpoint", PawnItemKind.CALLBACK),
        _item("OnRconCommand", "Callback triggered on server console RCON command", PawnItemKind.CALLBACK),
        _item("OnPlayerRequestSpawn", "Callback triggered when player clicks Spawn button", PawnItemKind.CALLBACK),
        _item("OnObjectMoved", "Callback triggered when a moving object finishes its travel", PawnItemKind.CALLBACK),
        _item("OnPlayerObjectMoved", "Callback triggered when player object finishes moving", PawnItemKind.CALLBACK),
        _item("OnPlayerPickUpPickup", "Callback triggered when a player walks over a pickup", PawnItemKind.CALLBACK),
        _item("OnVehicleMod", "Callback triggered when a vehicle modification is installed", PawnItemKind.CALLBACK),
        _item("OnVehiclePaintjob", "Callback triggered when a vehicle paintjob is applied", PawnItemKind.CALLBACK),
        _item("OnVehicleRespray", "Callback triggered when a vehicle color is changed", PawnItemKind.CALLBACK),
        _item("OnVehicleDamageStatusUpdate", "Callback triggered on vehicle damage parts update", PawnItemKind.CALLBACK),
        _item("OnUnoccupiedVehicleUpdate", "Callback triggered periodically for unoccupied vehicles", PawnItemKind.CALLBACK),
        _item("OnPlayerSelectedMenuRow", "Callback triggered when selecting a menu option", PawnItemKind.CALLBACK),
        _item("OnPlayerExitedMenu", "Callback triggered when player cancels out of a menu", PawnItemKind.CALLBACK),
        _item("OnDialogResponse", "Callback triggered when user interacts with a GUI dialog", PawnItemKind.CALLBACK),
        _item("OnPlayerClickPlayerTextDraw", "Callback triggered on clicking personal text draw", PawnItemKind.CALLBACK),
        _item("OnPlayerClickTextDraw", "Callback triggered on clicking global text draw", PawnItemKind.CALLBACK),
        _item("OnPlayerClickPlayer", "Callback triggered when clicking player in scoreboard", PawnItemKind.CALLBACK),
        _item("OnIncomingConnection", "Callback triggered on incoming raw client connection", PawnItemKind.CALLBACK),
        _item("OnTrailerUpdate", "Callback triggered when vehicle trailer sync updates", PawnItemKind.CALLBACK),
        _item("OnVehicleSirenStateChange", "Callback triggered when siren toggles on/off", PawnItemKind.CALLBACK),
        _item("OnPlayerGiveDamage", "Callback triggered when a player damages someone", PawnItemKind.CALLBACK),
        _item("OnPlayerTakeDamage", "Callback triggered when a player suffers damage", PawnItemKind.CALLBACK),
        _item("OnPlayerWeaponShot", "Callback triggered on bullet fired by weapon", PawnItemKind.CALLBACK)
    )

    val NATIVES: List<_item> = listOf(
        _item("print", "Prints a plain string to server log without formatting: print(const string[])", PawnItemKind.FUNCTION),
        _item("printf", "Prints formatted string to server log: printf(const format[], {Float,_}:...)", PawnItemKind.FUNCTION),
        _item("format", "Formats string buffer with specifiers: format(output[], len, const format[], ...)", PawnItemKind.FUNCTION),
        _item("strlen", "Returns length of string in characters: strlen(const string[])", PawnItemKind.FUNCTION),
        _item("strcat", "Concatenates two strings together: strcat(dest[], const source[], maxlength)", PawnItemKind.FUNCTION),
        _item("strdel", "Deletes characters from start to end index: strdel(string[], start, end)", PawnItemKind.FUNCTION),
        _item("strins", "Inserts string at given index: strins(string[], const substr[], pos, maxlength)", PawnItemKind.FUNCTION),
        _item("strcmp", "Compares two strings: strcmp(const string1[], const string2[], bool:ignorecase, length)", PawnItemKind.FUNCTION),
        _item("strfind", "Finds substring in target string: strfind(const string[], const sub[], bool:ignorecase, pos)", PawnItemKind.FUNCTION),
        _item("strval", "Converts string characters into an integer: strval(const string[])", PawnItemKind.FUNCTION),
        _item("valstr", "Converts integer into decimal string: valstr(dest[], value, bool:pack)", PawnItemKind.FUNCTION),
        _item("strmid", "Extracts substring from source into dest: strmid(dest[], const source[], start, end, maxlength)", PawnItemKind.FUNCTION),
        _item("SetTimer", "Calls function after interval in ms: SetTimer(funcname[], interval, repeating)", PawnItemKind.FUNCTION),
        _item("SetTimerEx", "Calls function with arguments after interval: SetTimerEx(funcname[], interval, repeating, format[], ...)", PawnItemKind.FUNCTION),
        _item("KillTimer", "Cancels an active timer: KillTimer(timerid)", PawnItemKind.FUNCTION),
        _item("GetTickCount", "Returns millisecond count since system start: GetTickCount()", PawnItemKind.FUNCTION),
        _item("GetMaxPlayers", "Returns configured player slot limit: GetMaxPlayers()", PawnItemKind.FUNCTION),
        _item("random", "Returns random integer between 0 and max - 1: random(max)", PawnItemKind.FUNCTION),
        _item("min", "Returns smaller of two integer values: min(value1, value2)", PawnItemKind.FUNCTION),
        _item("max", "Returns larger of two integer values: max(value1, value2)", PawnItemKind.FUNCTION),
        _item("clamp", "Constrains value between min and max bounds: clamp(value, min, max)", PawnItemKind.FUNCTION),
        _item("SendClientMessage", "Sends chat message to single player: SendClientMessage(playerid, color, const message[])", PawnItemKind.FUNCTION),
        _item("SendClientMessageToAll", "Broadcasts chat message to all players: SendClientMessageToAll(color, const message[])", PawnItemKind.FUNCTION),
        _item("SendPlayerMessageToPlayer", "Emulates chat from one player to another: SendPlayerMessageToPlayer(playerid, senderid, const message[])", PawnItemKind.FUNCTION),
        _item("SendPlayerMessageToAll", "Emulates chat from one player to all: SendPlayerMessageToAll(senderid, const message[])", PawnItemKind.FUNCTION),
        _item("SendRconCommand", "Executes an RCON command line directly: SendRconCommand(command[])", PawnItemKind.FUNCTION),
        _item("GameTextForAll", "Displays arcade style game text on all screens: GameTextForAll(const string[], time, style)", PawnItemKind.FUNCTION),
        _item("GameTextForPlayer", "Displays arcade style game text to player: GameTextForPlayer(playerid, const string[], time, style)", PawnItemKind.FUNCTION),
        _item("SetPlayerSpawnInfo", "Configures default spawn coordinates & gear: SetPlayerSpawnInfo(playerid, team, skin, Float:x, Float:y, Float:z, Float:rotation, ...)", PawnItemKind.FUNCTION),
        _item("SpawnPlayer", "Forces immediate respawn for player: SpawnPlayer(playerid)", PawnItemKind.FUNCTION),
        _item("SetPlayerPos", "Teleports player to 3D world coordinates: SetPlayerPos(playerid, Float:x, Float:y, Float:z)", PawnItemKind.FUNCTION),
        _item("SetPlayerPosFindZ", "Teleports player searching for highest ground: SetPlayerPosFindZ(playerid, Float:x, Float:y, Float:z)", PawnItemKind.FUNCTION),
        _item("GetPlayerPos", "Gets current 3D position of player: GetPlayerPos(playerid, &Float:x, &Float:y, &Float:z)", PawnItemKind.FUNCTION),
        _item("SetPlayerFacingAngle", "Sets heading compass direction angle: SetPlayerFacingAngle(playerid, Float:ang)", PawnItemKind.FUNCTION),
        _item("GetPlayerFacingAngle", "Gets heading compass direction angle: GetPlayerFacingAngle(playerid, &Float:ang)", PawnItemKind.FUNCTION),
        _item("IsPlayerInRangeOfPoint", "Tests if player is within sphere radius: IsPlayerInRangeOfPoint(playerid, Float:range, Float:x, Float:y, Float:z)", PawnItemKind.FUNCTION),
        _item("IsPlayerConnected", "Tests if player is connected: IsPlayerConnected(playerid)", PawnItemKind.FUNCTION),
        _item("IsPlayerInVehicle", "Tests if player is in specific vehicle: IsPlayerInVehicle(playerid, vehicleid)", PawnItemKind.FUNCTION),
        _item("IsPlayerInAnyVehicle", "Tests if player is inside any vehicle: IsPlayerInAnyVehicle(playerid)", PawnItemKind.FUNCTION),
        _item("SetPlayerHealth", "Sets current health points: SetPlayerHealth(playerid, Float:health)", PawnItemKind.FUNCTION),
        _item("GetPlayerHealth", "Gets current health points: GetPlayerHealth(playerid, &Float:health)", PawnItemKind.FUNCTION),
        _item("SetPlayerArmour", "Sets current body armour points: SetPlayerArmour(playerid, Float:armour)", PawnItemKind.FUNCTION),
        _item("GetPlayerArmour", "Gets current body armour points: GetPlayerArmour(playerid, &Float:armour)", PawnItemKind.FUNCTION),
        _item("GivePlayerMoney", "Adds or deducts money from player: GivePlayerMoney(playerid, money)", PawnItemKind.FUNCTION),
        _item("GetPlayerMoney", "Returns current player money balance: GetPlayerMoney(playerid)", PawnItemKind.FUNCTION),
        _item("ResetPlayerMoney", "Sets player money balance to zero: ResetPlayerMoney(playerid)", PawnItemKind.FUNCTION),
        _item("SetPlayerScore", "Sets player scoreboard score: SetPlayerScore(playerid, score)", PawnItemKind.FUNCTION),
        _item("GetPlayerScore", "Returns player scoreboard score: GetPlayerScore(playerid)", PawnItemKind.FUNCTION),
        _item("SetPlayerSkin", "Changes character PED skin ID: SetPlayerSkin(playerid, skinid)", PawnItemKind.FUNCTION),
        _item("GetPlayerSkin", "Returns current character PED skin ID: GetPlayerSkin(playerid)", PawnItemKind.FUNCTION),
        _item("GivePlayerWeapon", "Awards weapon and ammo to player: GivePlayerWeapon(playerid, weaponid, ammo)", PawnItemKind.FUNCTION),
        _item("ResetPlayerWeapons", "Removes all weapons from player: ResetPlayerWeapons(playerid)", PawnItemKind.FUNCTION),
        _item("GetPlayerWeapon", "Returns currently held weapon ID: GetPlayerWeapon(playerid)", PawnItemKind.FUNCTION),
        _item("SetPlayerName", "Changes player nickname: SetPlayerName(playerid, const name[])", PawnItemKind.FUNCTION),
        _item("GetPlayerName", "Retrieves player nickname: GetPlayerName(playerid, const name[], len)", PawnItemKind.FUNCTION),
        _item("SetPlayerColor", "Sets player radar blip and name tag color: SetPlayerColor(playerid, color)", PawnItemKind.FUNCTION),
        _item("GetPlayerColor", "Returns player color: GetPlayerColor(playerid)", PawnItemKind.FUNCTION),
        _item("SetPlayerInterior", "Changes building interior ID: SetPlayerInterior(playerid, interiorid)", PawnItemKind.FUNCTION),
        _item("GetPlayerInterior", "Gets building interior ID: GetPlayerInterior(playerid)", PawnItemKind.FUNCTION),
        _item("SetPlayerVirtualWorld", "Sets virtual world dimension ID: SetPlayerVirtualWorld(playerid, worldid)", PawnItemKind.FUNCTION),
        _item("GetPlayerVirtualWorld", "Gets virtual world dimension ID: GetPlayerVirtualWorld(playerid)", PawnItemKind.FUNCTION),
        _item("GetPlayerIp", "Retrieves player IP address: GetPlayerIp(playerid, const name[], len)", PawnItemKind.FUNCTION),
        _item("GetPlayerPing", "Returns player latency in ms: GetPlayerPing(playerid)", PawnItemKind.FUNCTION),
        _item("Kick", "Kicks a player from the server: Kick(playerid)", PawnItemKind.FUNCTION),
        _item("Ban", "Bans player IP address: Ban(playerid)", PawnItemKind.FUNCTION),
        _item("BanEx", "Bans player IP address with custom reason: BanEx(playerid, const reason[])", PawnItemKind.FUNCTION),
        _item("PutPlayerInVehicle", "Teleports player directly into vehicle: PutPlayerInVehicle(playerid, vehicleid, seatid)", PawnItemKind.FUNCTION),
        _item("RemovePlayerFromVehicle", "Ejects player from their vehicle: RemovePlayerFromVehicle(playerid)", PawnItemKind.FUNCTION),
        _item("GetPlayerVehicleID", "Returns vehicle ID player is inside: GetPlayerVehicleID(playerid)", PawnItemKind.FUNCTION),
        _item("GetPlayerVehicleSeat", "Returns vehicle seat index of player: GetPlayerVehicleSeat(playerid)", PawnItemKind.FUNCTION),
        _item("CreateVehicle", "Spawns a new vehicle: CreateVehicle(vehicletype, Float:x, Float:y, Float:z, Float:rotation, color1, color2, respawn_delay, addsiren)", PawnItemKind.FUNCTION),
        _item("DestroyVehicle", "Removes vehicle from the server: DestroyVehicle(vehicleid)", PawnItemKind.FUNCTION),
        _item("GetVehiclePos", "Gets 3D position of vehicle: GetVehiclePos(vehicleid, &Float:x, &Float:y, &Float:z)", PawnItemKind.FUNCTION),
        _item("SetVehiclePos", "Teleports vehicle to coordinates: SetVehiclePos(vehicleid, Float:x, Float:y, Float:z)", PawnItemKind.FUNCTION),
        _item("RepairVehicle", "Repairs vehicle engine and body panels: RepairVehicle(vehicleid)", PawnItemKind.FUNCTION),
        _item("SetVehicleNumberPlate", "Sets license plate text: SetVehicleNumberPlate(vehicleid, const numberplate[])", PawnItemKind.FUNCTION),
        _item("GetVehicleModel", "Returns vehicle model ID: GetVehicleModel(vehicleid)", PawnItemKind.FUNCTION),
        _item("AddVehicleComponent", "Adds car mod component: AddVehicleComponent(vehicleid, componentid)", PawnItemKind.FUNCTION),
        _item("RemoveVehicleComponent", "Removes car mod component: RemoveVehicleComponent(vehicleid, componentid)", PawnItemKind.FUNCTION),
        _item("ChangeVehicleColor", "Changes body color of vehicle: ChangeVehicleColor(vehicleid, color1, color2)", PawnItemKind.FUNCTION),
        _item("ChangeVehiclePaintjob", "Changes paintjob livery of vehicle: ChangeVehiclePaintjob(vehicleid, paintjobid)", PawnItemKind.FUNCTION),
        _item("CreateObject", "Creates global world object: CreateObject(modelid, Float:X, Float:Y, Float:Z, Float:rX, Float:rY, Float:rZ, Float:DrawDistance)", PawnItemKind.FUNCTION),
        _item("DestroyObject", "Destroys world object: DestroyObject(objectid)", PawnItemKind.FUNCTION),
        _item("MoveObject", "Smoothly moves object to target position: MoveObject(objectid, Float:X, Float:Y, Float:Z, Float:Speed, Float:RotX, Float:RotY, Float:RotZ)", PawnItemKind.FUNCTION),
        _item("StopObject", "Halts object movement immediately: StopObject(objectid)", PawnItemKind.FUNCTION),
        _item("CreatePickup", "Creates world pickup model: CreatePickup(model, type, Float:X, Float:Y, Float:Z, Virtualworld)", PawnItemKind.FUNCTION),
        _item("DestroyPickup", "Removes pickup: DestroyPickup(pickup)", PawnItemKind.FUNCTION),
        _item("ShowPlayerDialog", "Displays modal GUI dialog window to player: ShowPlayerDialog(playerid, dialogid, style, caption[], info[], button1[], button2[])", PawnItemKind.FUNCTION),
        _item("TextDrawCreate", "Creates global on-screen text draw: TextDrawCreate(Float:x, Float:y, text[])", PawnItemKind.FUNCTION),
        _item("TextDrawDestroy", "Destroys global text draw: TextDrawDestroy(Text:text)", PawnItemKind.FUNCTION),
        _item("TextDrawShowForPlayer", "Displays text draw to player: TextDrawShowForPlayer(playerid, Text:text)", PawnItemKind.FUNCTION),
        _item("TextDrawHideForPlayer", "Hides text draw from player: TextDrawHideForPlayer(playerid, Text:text)", PawnItemKind.FUNCTION),
        _item("TextDrawSetString", "Updates text draw string: TextDrawSetString(Text:text, string[])", PawnItemKind.FUNCTION),
        _item("fopen", "Opens file on disk: fopen(name[], mode)", PawnItemKind.FUNCTION),
        _item("fclose", "Closes open file handle: fclose(File:handle)", PawnItemKind.FUNCTION),
        _item("fwrite", "Writes text line to file: fwrite(File:handle, string[])", PawnItemKind.FUNCTION),
        _item("fread", "Reads text line from file: fread(File:handle, string[], size, bool:pack)", PawnItemKind.FUNCTION),
        _item("fexist", "Checks if file exists on server disk: fexist(name[])", PawnItemKind.FUNCTION),
        _item("fremove", "Deletes file from server disk: fremove(name[])", PawnItemKind.FUNCTION)
    )

    private val allItems: List<_item> by lazy {
        buildList {
            addAll(DIRECTIVES)
            addAll(KEYWORDS)
            addAll(TYPES)
            addAll(CONSTANTS)
            addAll(CALLBACKS)
            addAll(NATIVES)
            addAll(OPERATORS)
            addAll(includeItems())
        }
    }

    /**
     * Include symbols that are not already described by [NATIVES]/[CALLBACKS].
     *
     * They are synthesised from [PawnIndex] so autocompletion offers the
     * complete SA-MP surface, while the curated lists keep their richer
     * descriptions for the symbols they already own.
     */
    private fun includeItems(): List<_item> = buildList {
        val natives = PawnIndex.INCLUDES.flatMap { it.natives }
            .filterNot { it in nativeSet || it in keywordSet }
            .distinct()
        addAll(natives.map { _item(it, PawnIndex.NATIVE_DESCRIPTION, PawnItemKind.FUNCTION) })

        val forwards = PawnIndex.INCLUDES.flatMap { it.forwards }
            .filterNot { it in callbackSet || it in keywordSet }
            .distinct()
        addAll(forwards.map { _item(it, PawnIndex.CALLBACK_DESCRIPTION, PawnItemKind.CALLBACK) })
    }

    private val keywordSet: Set<String> by lazy {
        KEYWORDS.map { it.name }.toSet()
    }

    /** Include-derived natives, used for highlighting and completion. */
    private val includeNativeSet: Set<String> by lazy {
        PawnIndex.INCLUDES.flatMap { it.natives }.toSet()
    }

    /** Include-derived forwards, used for highlighting and completion. */
    private val includeForwardSet: Set<String> by lazy {
        PawnIndex.INCLUDES.flatMap { it.forwards }.toSet()
    }

    /**
     * Multi-character operators, longest first so the lexer can consume a
     * greedy match without leaving a dangling suffix behind.
     */
    val LONG_OPERATORS: List<String> by lazy {
        OPERATORS.map { it.name }.filter { it.length > 1 }.sortedByDescending { it.length }
    }

    /** First characters of any known operator. */
    val OPERATOR_HEADS: Set<Char> by lazy {
        OPERATORS.mapTo(mutableSetOf()) { it.name[0] }
    }

    private val directiveSet: Set<String> by lazy {
        DIRECTIVES.map { it.name.removePrefix("#") }.toSet()
    }

    private val typeSet: Set<String> by lazy {
        TYPES.map { it.name.removeSuffix(":") }.toSet()
    }

    private val constantSet: Set<String> by lazy {
        CONSTANTS.map { it.name }.toSet()
    }

    private val callbackSet: Set<String> by lazy {
        CALLBACKS.map { it.name }.toSet()
    }

    private val nativeSet: Set<String> by lazy {
        NATIVES.map { it.name }.toSet()
    }

    fun isKeyword(word: String): Boolean = word in keywordSet
    fun isDirective(word: String): Boolean = word in directiveSet || word.removePrefix("#") in directiveSet
    fun isType(word: String): Boolean = word in typeSet || word.removeSuffix(":") in typeSet
    fun isConstant(word: String): Boolean = word in constantSet

    /**
     * True for every known callable symbol, including the natives and forwards
     * that only come from the include table.
     */
    fun isFunction(word: String): Boolean =
        word in nativeSet || word in callbackSet || word in includeNativeSet || word in includeForwardSet

    /** True when [text] is a complete operator at the start of a token. */
    fun isOperator(text: String): Boolean = text in operatorSet

    private val operatorSet: Set<String> by lazy {
        OPERATORS.mapTo(mutableSetOf()) { it.name }
    }

    fun getCompletions(prefix: String): List<_item> {
        val query = prefix.trim()
        if (query.isEmpty()) return emptyList()
        val queryLower = query.lowercase()
        return allItems.filter { item ->
            item.name.lowercase().startsWith(queryLower) ||
                (query.startsWith("#") && item.name.startsWith(query, ignoreCase = true)) ||
                item.name.removePrefix("#").lowercase().startsWith(queryLower)
        }.take(35)
    }
}
