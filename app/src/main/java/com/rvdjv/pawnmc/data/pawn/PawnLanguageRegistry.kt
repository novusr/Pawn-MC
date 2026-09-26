package com.rvdjv.pawnmc.data.pawn

enum class PawnItemKind {
    KEYWORD,
    DIRECTIVE,
    TYPE,
    CONSTANT,
    FUNCTION,
    CALLBACK
}

data class PawnLanguageItem(
    val name: String,
    val description: String,
    val kind: PawnItemKind
)

/**
 * Dedicated Pawn language registry holding official Pawn specifications,
 * keywords, preprocessor directives, types, constants, SA-MP callbacks and native functions.
 * Stored in the data layer separately from the UI.
 */
object PawnLanguageRegistry {

    val DIRECTIVES: List<PawnLanguageItem> = listOf(
        PawnLanguageItem("#include", "Include an external source or header file", PawnItemKind.DIRECTIVE),
        PawnLanguageItem("#define", "Define a preprocessor macro or constant", PawnItemKind.DIRECTIVE),
        PawnLanguageItem("#undef", "Undefine an existing macro", PawnItemKind.DIRECTIVE),
        PawnLanguageItem("#if", "Conditional compilation check", PawnItemKind.DIRECTIVE),
        PawnLanguageItem("#elseif", "Alternative conditional compilation check", PawnItemKind.DIRECTIVE),
        PawnLanguageItem("#else", "Default conditional compilation branch", PawnItemKind.DIRECTIVE),
        PawnLanguageItem("#endif", "End of conditional compilation block", PawnItemKind.DIRECTIVE),
        PawnLanguageItem("#endinput", "Stops reading the current file", PawnItemKind.DIRECTIVE),
        PawnLanguageItem("#assert", "Compile-time assertion check", PawnItemKind.DIRECTIVE),
        PawnLanguageItem("#error", "Emit user-defined compilation error", PawnItemKind.DIRECTIVE),
        PawnLanguageItem("#pragma", "Compiler control directive", PawnItemKind.DIRECTIVE),
        PawnLanguageItem("#emit", "Emit raw AMX bytecode opcode", PawnItemKind.DIRECTIVE),
        PawnLanguageItem("#line", "Set line number and optional filename", PawnItemKind.DIRECTIVE),
        PawnLanguageItem("tabsize", "Pragma to set tab indent width", PawnItemKind.DIRECTIVE),
        PawnLanguageItem("dynamic", "Pragma to set AMX stack/heap memory size", PawnItemKind.DIRECTIVE),
        PawnLanguageItem("ctrlchar", "Pragma to change escape character", PawnItemKind.DIRECTIVE),
        PawnLanguageItem("deprecated", "Pragma to mark symbol as deprecated", PawnItemKind.DIRECTIVE),
        PawnLanguageItem("semicolon", "Pragma to enforce or relax semicolons", PawnItemKind.DIRECTIVE),
        PawnLanguageItem("warning", "Pragma to enable/disable specific warnings", PawnItemKind.DIRECTIVE)
    )

    val KEYWORDS: List<PawnLanguageItem> = listOf(
        PawnLanguageItem("stock", "Declares variable or function compiled only if used", PawnItemKind.KEYWORD),
        PawnLanguageItem("public", "Declares an exported function callable by AMX", PawnItemKind.KEYWORD),
        PawnLanguageItem("forward", "Forward declaration for public functions", PawnItemKind.KEYWORD),
        PawnLanguageItem("native", "Declares a C/C++ native plugin or host function", PawnItemKind.KEYWORD),
        PawnLanguageItem("new", "Declares a local or global variable", PawnItemKind.KEYWORD),
        PawnLanguageItem("static", "Declares static scoped variable or private function", PawnItemKind.KEYWORD),
        PawnLanguageItem("const", "Declares an immutable constant variable", PawnItemKind.KEYWORD),
        PawnLanguageItem("enum", "Defines an enumerated constant structure", PawnItemKind.KEYWORD),
        PawnLanguageItem("state", "State machine transition declaration", PawnItemKind.KEYWORD),
        PawnLanguageItem("goto", "Jump unconditionally to label", PawnItemKind.KEYWORD),
        PawnLanguageItem("break", "Break out of loop or switch block", PawnItemKind.KEYWORD),
        PawnLanguageItem("continue", "Skip to next iteration of loop", PawnItemKind.KEYWORD),
        PawnLanguageItem("return", "Return value and exit function", PawnItemKind.KEYWORD),
        PawnLanguageItem("if", "Conditional branch statement", PawnItemKind.KEYWORD),
        PawnLanguageItem("else", "Alternative conditional branch", PawnItemKind.KEYWORD),
        PawnLanguageItem("while", "Loop while condition is true", PawnItemKind.KEYWORD),
        PawnLanguageItem("do", "Loop executing at least once", PawnItemKind.KEYWORD),
        PawnLanguageItem("for", "Standard for-loop statement", PawnItemKind.KEYWORD),
        PawnLanguageItem("switch", "Multi-branch switch statement", PawnItemKind.KEYWORD),
        PawnLanguageItem("case", "Case label in switch statement", PawnItemKind.KEYWORD),
        PawnLanguageItem("default", "Default branch in switch statement", PawnItemKind.KEYWORD),
        PawnLanguageItem("sizeof", "Returns size in cells or array dimensions", PawnItemKind.KEYWORD),
        PawnLanguageItem("tagof", "Returns tag integer representing data type", PawnItemKind.KEYWORD),
        PawnLanguageItem("assert", "Runtime assertion condition", PawnItemKind.KEYWORD),
        PawnLanguageItem("sleep", "Pawn abstract machine sleep call", PawnItemKind.KEYWORD),
        PawnLanguageItem("char", "Sub-cell character packing indexer", PawnItemKind.KEYWORD)
    )

    val TYPES: List<PawnLanguageItem> = listOf(
        PawnLanguageItem("Float:", "Floating point single-precision tag", PawnItemKind.TYPE),
        PawnLanguageItem("bool:", "Boolean true/false data tag", PawnItemKind.TYPE),
        PawnLanguageItem("File:", "File handle tag for file operations", PawnItemKind.TYPE),
        PawnLanguageItem("Text:", "Global text draw identifier tag", PawnItemKind.TYPE),
        PawnLanguageItem("PlayerText:", "Player-specific text draw identifier tag", PawnItemKind.TYPE),
        PawnLanguageItem("Menu:", "Game menu identifier tag", PawnItemKind.TYPE),
        PawnLanguageItem("Text3D:", "Global 3D text label identifier tag", PawnItemKind.TYPE),
        PawnLanguageItem("PlayerText3D:", "Player-specific 3D text label tag", PawnItemKind.TYPE)
    )

    val CONSTANTS: List<PawnLanguageItem> = listOf(
        PawnLanguageItem("true", "Boolean true literal (1)", PawnItemKind.CONSTANT),
        PawnLanguageItem("false", "Boolean false literal (0)", PawnItemKind.CONSTANT),
        PawnLanguageItem("cellmax", "Maximum signed 32-bit cell value", PawnItemKind.CONSTANT),
        PawnLanguageItem("cellmin", "Minimum signed 32-bit cell value", PawnItemKind.CONSTANT),
        PawnLanguageItem("INVALID_PLAYER_ID", "Special ID representing no valid player (65535)", PawnItemKind.CONSTANT),
        PawnLanguageItem("INVALID_VEHICLE_ID", "Special ID representing no valid vehicle (65535)", PawnItemKind.CONSTANT),
        PawnLanguageItem("INVALID_OBJECT_ID", "Special ID representing no valid object (65535)", PawnItemKind.CONSTANT),
        PawnLanguageItem("INVALID_TIMER_ID", "Invalid timer handle representation", PawnItemKind.CONSTANT),
        PawnLanguageItem("MAX_PLAYERS", "Maximum player slots on server (default 50/500/1000)", PawnItemKind.CONSTANT),
        PawnLanguageItem("MAX_VEHICLES", "Maximum vehicle slots on server (default 2000)", PawnItemKind.CONSTANT),
        PawnLanguageItem("MAX_OBJECTS", "Maximum global object limit (1000)", PawnItemKind.CONSTANT),
        PawnLanguageItem("MAX_ACTORS", "Maximum static actors on server (1000)", PawnItemKind.CONSTANT),
        PawnLanguageItem("MAX_TEXT_DRAWS", "Maximum global text draws (2048)", PawnItemKind.CONSTANT),
        PawnLanguageItem("MAX_MENUS", "Maximum menu instances (128)", PawnItemKind.CONSTANT),
        PawnLanguageItem("MAX_GANG_ZONES", "Maximum gang zones (1024)", PawnItemKind.CONSTANT),
        PawnLanguageItem("SPECIAL_ACTION_NONE", "Player special action: None", PawnItemKind.CONSTANT),
        PawnLanguageItem("SPECIAL_ACTION_DUCK", "Player special action: Ducking", PawnItemKind.CONSTANT),
        PawnLanguageItem("SPECIAL_ACTION_USEJETPACK", "Player special action: Jetpack", PawnItemKind.CONSTANT),
        PawnLanguageItem("PLAYER_STATE_NONE", "Player state: None", PawnItemKind.CONSTANT),
        PawnLanguageItem("PLAYER_STATE_ONFOOT", "Player state: On Foot", PawnItemKind.CONSTANT),
        PawnLanguageItem("PLAYER_STATE_DRIVER", "Player state: Driver of a vehicle", PawnItemKind.CONSTANT),
        PawnLanguageItem("PLAYER_STATE_PASSENGER", "Player state: Passenger in a vehicle", PawnItemKind.CONSTANT),
        PawnLanguageItem("PLAYER_STATE_WASTED", "Player state: Dead/Wasted", PawnItemKind.CONSTANT),
        PawnLanguageItem("PLAYER_STATE_SPAWNED", "Player state: Spawned", PawnItemKind.CONSTANT),
        PawnLanguageItem("PLAYER_STATE_SPECTATING", "Player state: Spectating", PawnItemKind.CONSTANT)
    )

    val CALLBACKS: List<PawnLanguageItem> = listOf(
        PawnLanguageItem("OnGameModeInit", "Callback triggered when the gamemode loads", PawnItemKind.CALLBACK),
        PawnLanguageItem("OnGameModeExit", "Callback triggered when the gamemode shuts down", PawnItemKind.CALLBACK),
        PawnLanguageItem("OnFilterScriptInit", "Callback triggered when a filterscript loads", PawnItemKind.CALLBACK),
        PawnLanguageItem("OnFilterScriptExit", "Callback triggered when a filterscript unloads", PawnItemKind.CALLBACK),
        PawnLanguageItem("OnPlayerConnect", "Callback triggered when a player joins the server", PawnItemKind.CALLBACK),
        PawnLanguageItem("OnPlayerDisconnect", "Callback triggered when a player leaves the server", PawnItemKind.CALLBACK),
        PawnLanguageItem("OnPlayerSpawn", "Callback triggered when a player spawns in the world", PawnItemKind.CALLBACK),
        PawnLanguageItem("OnPlayerDeath", "Callback triggered when a player dies or is killed", PawnItemKind.CALLBACK),
        PawnLanguageItem("OnVehicleSpawn", "Callback triggered when a vehicle spawns", PawnItemKind.CALLBACK),
        PawnLanguageItem("OnVehicleDeath", "Callback triggered when a vehicle is destroyed", PawnItemKind.CALLBACK),
        PawnLanguageItem("OnPlayerText", "Callback triggered when a player sends public chat", PawnItemKind.CALLBACK),
        PawnLanguageItem("OnPlayerCommandText", "Callback triggered when a player enters a slash command", PawnItemKind.CALLBACK),
        PawnLanguageItem("OnPlayerRequestClass", "Callback triggered when a player views class selection", PawnItemKind.CALLBACK),
        PawnLanguageItem("OnPlayerEnterVehicle", "Callback triggered when a player starts entering a vehicle", PawnItemKind.CALLBACK),
        PawnLanguageItem("OnPlayerExitVehicle", "Callback triggered when a player starts exiting a vehicle", PawnItemKind.CALLBACK),
        PawnLanguageItem("OnPlayerStateChange", "Callback triggered when a player changes state (foot, car, etc.)", PawnItemKind.CALLBACK),
        PawnLanguageItem("OnPlayerEnterCheckpoint", "Callback triggered when a player enters standard checkpoint", PawnItemKind.CALLBACK),
        PawnLanguageItem("OnPlayerLeaveCheckpoint", "Callback triggered when a player leaves standard checkpoint", PawnItemKind.CALLBACK),
        PawnLanguageItem("OnPlayerEnterRaceCheckpoint", "Callback triggered when entering race checkpoint", PawnItemKind.CALLBACK),
        PawnLanguageItem("OnPlayerLeaveRaceCheckpoint", "Callback triggered when leaving race checkpoint", PawnItemKind.CALLBACK),
        PawnLanguageItem("OnRconCommand", "Callback triggered on server console RCON command", PawnItemKind.CALLBACK),
        PawnLanguageItem("OnPlayerRequestSpawn", "Callback triggered when player clicks Spawn button", PawnItemKind.CALLBACK),
        PawnLanguageItem("OnObjectMoved", "Callback triggered when a moving object finishes its travel", PawnItemKind.CALLBACK),
        PawnLanguageItem("OnPlayerObjectMoved", "Callback triggered when player object finishes moving", PawnItemKind.CALLBACK),
        PawnLanguageItem("OnPlayerPickUpPickup", "Callback triggered when a player walks over a pickup", PawnItemKind.CALLBACK),
        PawnLanguageItem("OnVehicleMod", "Callback triggered when a vehicle modification is installed", PawnItemKind.CALLBACK),
        PawnLanguageItem("OnVehiclePaintjob", "Callback triggered when a vehicle paintjob is applied", PawnItemKind.CALLBACK),
        PawnLanguageItem("OnVehicleRespray", "Callback triggered when a vehicle color is changed", PawnItemKind.CALLBACK),
        PawnLanguageItem("OnVehicleDamageStatusUpdate", "Callback triggered on vehicle damage parts update", PawnItemKind.CALLBACK),
        PawnLanguageItem("OnUnoccupiedVehicleUpdate", "Callback triggered periodically for unoccupied vehicles", PawnItemKind.CALLBACK),
        PawnLanguageItem("OnPlayerSelectedMenuRow", "Callback triggered when selecting a menu option", PawnItemKind.CALLBACK),
        PawnLanguageItem("OnPlayerExitedMenu", "Callback triggered when player cancels out of a menu", PawnItemKind.CALLBACK),
        PawnLanguageItem("OnDialogResponse", "Callback triggered when user interacts with a GUI dialog", PawnItemKind.CALLBACK),
        PawnLanguageItem("OnPlayerClickPlayerTextDraw", "Callback triggered on clicking personal text draw", PawnItemKind.CALLBACK),
        PawnLanguageItem("OnPlayerClickTextDraw", "Callback triggered on clicking global text draw", PawnItemKind.CALLBACK),
        PawnLanguageItem("OnPlayerClickPlayer", "Callback triggered when clicking player in scoreboard", PawnItemKind.CALLBACK),
        PawnLanguageItem("OnIncomingConnection", "Callback triggered on incoming raw client connection", PawnItemKind.CALLBACK),
        PawnLanguageItem("OnTrailerUpdate", "Callback triggered when vehicle trailer sync updates", PawnItemKind.CALLBACK),
        PawnLanguageItem("OnVehicleSirenStateChange", "Callback triggered when siren toggles on/off", PawnItemKind.CALLBACK),
        PawnLanguageItem("OnPlayerGiveDamage", "Callback triggered when a player damages someone", PawnItemKind.CALLBACK),
        PawnLanguageItem("OnPlayerTakeDamage", "Callback triggered when a player suffers damage", PawnItemKind.CALLBACK),
        PawnLanguageItem("OnPlayerWeaponShot", "Callback triggered on bullet fired by weapon", PawnItemKind.CALLBACK)
    )

    val NATIVES: List<PawnLanguageItem> = listOf(
        PawnLanguageItem("print", "Prints a plain string to server log without formatting: print(const string[])", PawnItemKind.FUNCTION),
        PawnLanguageItem("printf", "Prints formatted string to server log: printf(const format[], {Float,_}:...)", PawnItemKind.FUNCTION),
        PawnLanguageItem("format", "Formats string buffer with specifiers: format(output[], len, const format[], ...)", PawnItemKind.FUNCTION),
        PawnLanguageItem("strlen", "Returns length of string in characters: strlen(const string[])", PawnItemKind.FUNCTION),
        PawnLanguageItem("strcat", "Concatenates two strings together: strcat(dest[], const source[], maxlength)", PawnItemKind.FUNCTION),
        PawnLanguageItem("strdel", "Deletes characters from start to end index: strdel(string[], start, end)", PawnItemKind.FUNCTION),
        PawnLanguageItem("strins", "Inserts string at given index: strins(string[], const substr[], pos, maxlength)", PawnItemKind.FUNCTION),
        PawnLanguageItem("strcmp", "Compares two strings: strcmp(const string1[], const string2[], bool:ignorecase, length)", PawnItemKind.FUNCTION),
        PawnLanguageItem("strfind", "Finds substring in target string: strfind(const string[], const sub[], bool:ignorecase, pos)", PawnItemKind.FUNCTION),
        PawnLanguageItem("strval", "Converts string characters into an integer: strval(const string[])", PawnItemKind.FUNCTION),
        PawnLanguageItem("valstr", "Converts integer into decimal string: valstr(dest[], value, bool:pack)", PawnItemKind.FUNCTION),
        PawnLanguageItem("strmid", "Extracts substring from source into dest: strmid(dest[], const source[], start, end, maxlength)", PawnItemKind.FUNCTION),
        PawnLanguageItem("SetTimer", "Calls function after interval in ms: SetTimer(funcname[], interval, repeating)", PawnItemKind.FUNCTION),
        PawnLanguageItem("SetTimerEx", "Calls function with arguments after interval: SetTimerEx(funcname[], interval, repeating, format[], ...)", PawnItemKind.FUNCTION),
        PawnLanguageItem("KillTimer", "Cancels an active timer: KillTimer(timerid)", PawnItemKind.FUNCTION),
        PawnLanguageItem("GetTickCount", "Returns millisecond count since system start: GetTickCount()", PawnItemKind.FUNCTION),
        PawnLanguageItem("GetMaxPlayers", "Returns configured player slot limit: GetMaxPlayers()", PawnItemKind.FUNCTION),
        PawnLanguageItem("random", "Returns random integer between 0 and max - 1: random(max)", PawnItemKind.FUNCTION),
        PawnLanguageItem("min", "Returns smaller of two integer values: min(value1, value2)", PawnItemKind.FUNCTION),
        PawnLanguageItem("max", "Returns larger of two integer values: max(value1, value2)", PawnItemKind.FUNCTION),
        PawnLanguageItem("clamp", "Constrains value between min and max bounds: clamp(value, min, max)", PawnItemKind.FUNCTION),
        PawnLanguageItem("SendClientMessage", "Sends chat message to single player: SendClientMessage(playerid, color, const message[])", PawnItemKind.FUNCTION),
        PawnLanguageItem("SendClientMessageToAll", "Broadcasts chat message to all players: SendClientMessageToAll(color, const message[])", PawnItemKind.FUNCTION),
        PawnLanguageItem("SendPlayerMessageToPlayer", "Emulates chat from one player to another: SendPlayerMessageToPlayer(playerid, senderid, const message[])", PawnItemKind.FUNCTION),
        PawnLanguageItem("SendPlayerMessageToAll", "Emulates chat from one player to all: SendPlayerMessageToAll(senderid, const message[])", PawnItemKind.FUNCTION),
        PawnLanguageItem("SendRconCommand", "Executes an RCON command line directly: SendRconCommand(command[])", PawnItemKind.FUNCTION),
        PawnLanguageItem("GameTextForAll", "Displays arcade style game text on all screens: GameTextForAll(const string[], time, style)", PawnItemKind.FUNCTION),
        PawnLanguageItem("GameTextForPlayer", "Displays arcade style game text to player: GameTextForPlayer(playerid, const string[], time, style)", PawnItemKind.FUNCTION),
        PawnLanguageItem("SetPlayerSpawnInfo", "Configures default spawn coordinates & gear: SetPlayerSpawnInfo(playerid, team, skin, Float:x, Float:y, Float:z, Float:rotation, ...)", PawnItemKind.FUNCTION),
        PawnLanguageItem("SpawnPlayer", "Forces immediate respawn for player: SpawnPlayer(playerid)", PawnItemKind.FUNCTION),
        PawnLanguageItem("SetPlayerPos", "Teleports player to 3D world coordinates: SetPlayerPos(playerid, Float:x, Float:y, Float:z)", PawnItemKind.FUNCTION),
        PawnLanguageItem("SetPlayerPosFindZ", "Teleports player searching for highest ground: SetPlayerPosFindZ(playerid, Float:x, Float:y, Float:z)", PawnItemKind.FUNCTION),
        PawnLanguageItem("GetPlayerPos", "Gets current 3D position of player: GetPlayerPos(playerid, &Float:x, &Float:y, &Float:z)", PawnItemKind.FUNCTION),
        PawnLanguageItem("SetPlayerFacingAngle", "Sets heading compass direction angle: SetPlayerFacingAngle(playerid, Float:ang)", PawnItemKind.FUNCTION),
        PawnLanguageItem("GetPlayerFacingAngle", "Gets heading compass direction angle: GetPlayerFacingAngle(playerid, &Float:ang)", PawnItemKind.FUNCTION),
        PawnLanguageItem("IsPlayerInRangeOfPoint", "Tests if player is within sphere radius: IsPlayerInRangeOfPoint(playerid, Float:range, Float:x, Float:y, Float:z)", PawnItemKind.FUNCTION),
        PawnLanguageItem("IsPlayerConnected", "Tests if player is connected: IsPlayerConnected(playerid)", PawnItemKind.FUNCTION),
        PawnLanguageItem("IsPlayerInVehicle", "Tests if player is in specific vehicle: IsPlayerInVehicle(playerid, vehicleid)", PawnItemKind.FUNCTION),
        PawnLanguageItem("IsPlayerInAnyVehicle", "Tests if player is inside any vehicle: IsPlayerInAnyVehicle(playerid)", PawnItemKind.FUNCTION),
        PawnLanguageItem("SetPlayerHealth", "Sets current health points: SetPlayerHealth(playerid, Float:health)", PawnItemKind.FUNCTION),
        PawnLanguageItem("GetPlayerHealth", "Gets current health points: GetPlayerHealth(playerid, &Float:health)", PawnItemKind.FUNCTION),
        PawnLanguageItem("SetPlayerArmour", "Sets current body armour points: SetPlayerArmour(playerid, Float:armour)", PawnItemKind.FUNCTION),
        PawnLanguageItem("GetPlayerArmour", "Gets current body armour points: GetPlayerArmour(playerid, &Float:armour)", PawnItemKind.FUNCTION),
        PawnLanguageItem("GivePlayerMoney", "Adds or deducts money from player: GivePlayerMoney(playerid, money)", PawnItemKind.FUNCTION),
        PawnLanguageItem("GetPlayerMoney", "Returns current player money balance: GetPlayerMoney(playerid)", PawnItemKind.FUNCTION),
        PawnLanguageItem("ResetPlayerMoney", "Sets player money balance to zero: ResetPlayerMoney(playerid)", PawnItemKind.FUNCTION),
        PawnLanguageItem("SetPlayerScore", "Sets player scoreboard score: SetPlayerScore(playerid, score)", PawnItemKind.FUNCTION),
        PawnLanguageItem("GetPlayerScore", "Returns player scoreboard score: GetPlayerScore(playerid)", PawnItemKind.FUNCTION),
        PawnLanguageItem("SetPlayerSkin", "Changes character PED skin ID: SetPlayerSkin(playerid, skinid)", PawnItemKind.FUNCTION),
        PawnLanguageItem("GetPlayerSkin", "Returns current character PED skin ID: GetPlayerSkin(playerid)", PawnItemKind.FUNCTION),
        PawnLanguageItem("GivePlayerWeapon", "Awards weapon and ammo to player: GivePlayerWeapon(playerid, weaponid, ammo)", PawnItemKind.FUNCTION),
        PawnLanguageItem("ResetPlayerWeapons", "Removes all weapons from player: ResetPlayerWeapons(playerid)", PawnItemKind.FUNCTION),
        PawnLanguageItem("GetPlayerWeapon", "Returns currently held weapon ID: GetPlayerWeapon(playerid)", PawnItemKind.FUNCTION),
        PawnLanguageItem("SetPlayerName", "Changes player nickname: SetPlayerName(playerid, const name[])", PawnItemKind.FUNCTION),
        PawnLanguageItem("GetPlayerName", "Retrieves player nickname: GetPlayerName(playerid, const name[], len)", PawnItemKind.FUNCTION),
        PawnLanguageItem("SetPlayerColor", "Sets player radar blip and name tag color: SetPlayerColor(playerid, color)", PawnItemKind.FUNCTION),
        PawnLanguageItem("GetPlayerColor", "Returns player color: GetPlayerColor(playerid)", PawnItemKind.FUNCTION),
        PawnLanguageItem("SetPlayerInterior", "Changes building interior ID: SetPlayerInterior(playerid, interiorid)", PawnItemKind.FUNCTION),
        PawnLanguageItem("GetPlayerInterior", "Gets building interior ID: GetPlayerInterior(playerid)", PawnItemKind.FUNCTION),
        PawnLanguageItem("SetPlayerVirtualWorld", "Sets virtual world dimension ID: SetPlayerVirtualWorld(playerid, worldid)", PawnItemKind.FUNCTION),
        PawnLanguageItem("GetPlayerVirtualWorld", "Gets virtual world dimension ID: GetPlayerVirtualWorld(playerid)", PawnItemKind.FUNCTION),
        PawnLanguageItem("GetPlayerIp", "Retrieves player IP address: GetPlayerIp(playerid, const name[], len)", PawnItemKind.FUNCTION),
        PawnLanguageItem("GetPlayerPing", "Returns player latency in ms: GetPlayerPing(playerid)", PawnItemKind.FUNCTION),
        PawnLanguageItem("Kick", "Kicks a player from the server: Kick(playerid)", PawnItemKind.FUNCTION),
        PawnLanguageItem("Ban", "Bans player IP address: Ban(playerid)", PawnItemKind.FUNCTION),
        PawnLanguageItem("BanEx", "Bans player IP address with custom reason: BanEx(playerid, const reason[])", PawnItemKind.FUNCTION),
        PawnLanguageItem("PutPlayerInVehicle", "Teleports player directly into vehicle: PutPlayerInVehicle(playerid, vehicleid, seatid)", PawnItemKind.FUNCTION),
        PawnLanguageItem("RemovePlayerFromVehicle", "Ejects player from their vehicle: RemovePlayerFromVehicle(playerid)", PawnItemKind.FUNCTION),
        PawnLanguageItem("GetPlayerVehicleID", "Returns vehicle ID player is inside: GetPlayerVehicleID(playerid)", PawnItemKind.FUNCTION),
        PawnLanguageItem("GetPlayerVehicleSeat", "Returns vehicle seat index of player: GetPlayerVehicleSeat(playerid)", PawnItemKind.FUNCTION),
        PawnLanguageItem("CreateVehicle", "Spawns a new vehicle: CreateVehicle(vehicletype, Float:x, Float:y, Float:z, Float:rotation, color1, color2, respawn_delay, addsiren)", PawnItemKind.FUNCTION),
        PawnLanguageItem("DestroyVehicle", "Removes vehicle from the server: DestroyVehicle(vehicleid)", PawnItemKind.FUNCTION),
        PawnLanguageItem("GetVehiclePos", "Gets 3D position of vehicle: GetVehiclePos(vehicleid, &Float:x, &Float:y, &Float:z)", PawnItemKind.FUNCTION),
        PawnLanguageItem("SetVehiclePos", "Teleports vehicle to coordinates: SetVehiclePos(vehicleid, Float:x, Float:y, Float:z)", PawnItemKind.FUNCTION),
        PawnLanguageItem("RepairVehicle", "Repairs vehicle engine and body panels: RepairVehicle(vehicleid)", PawnItemKind.FUNCTION),
        PawnLanguageItem("SetVehicleNumberPlate", "Sets license plate text: SetVehicleNumberPlate(vehicleid, const numberplate[])", PawnItemKind.FUNCTION),
        PawnLanguageItem("GetVehicleModel", "Returns vehicle model ID: GetVehicleModel(vehicleid)", PawnItemKind.FUNCTION),
        PawnLanguageItem("AddVehicleComponent", "Adds car mod component: AddVehicleComponent(vehicleid, componentid)", PawnItemKind.FUNCTION),
        PawnLanguageItem("RemoveVehicleComponent", "Removes car mod component: RemoveVehicleComponent(vehicleid, componentid)", PawnItemKind.FUNCTION),
        PawnLanguageItem("ChangeVehicleColor", "Changes body color of vehicle: ChangeVehicleColor(vehicleid, color1, color2)", PawnItemKind.FUNCTION),
        PawnLanguageItem("ChangeVehiclePaintjob", "Changes paintjob livery of vehicle: ChangeVehiclePaintjob(vehicleid, paintjobid)", PawnItemKind.FUNCTION),
        PawnLanguageItem("CreateObject", "Creates global world object: CreateObject(modelid, Float:X, Float:Y, Float:Z, Float:rX, Float:rY, Float:rZ, Float:DrawDistance)", PawnItemKind.FUNCTION),
        PawnLanguageItem("DestroyObject", "Destroys world object: DestroyObject(objectid)", PawnItemKind.FUNCTION),
        PawnLanguageItem("MoveObject", "Smoothly moves object to target position: MoveObject(objectid, Float:X, Float:Y, Float:Z, Float:Speed, Float:RotX, Float:RotY, Float:RotZ)", PawnItemKind.FUNCTION),
        PawnLanguageItem("StopObject", "Halts object movement immediately: StopObject(objectid)", PawnItemKind.FUNCTION),
        PawnLanguageItem("CreatePickup", "Creates world pickup model: CreatePickup(model, type, Float:X, Float:Y, Float:Z, Virtualworld)", PawnItemKind.FUNCTION),
        PawnLanguageItem("DestroyPickup", "Removes pickup: DestroyPickup(pickup)", PawnItemKind.FUNCTION),
        PawnLanguageItem("ShowPlayerDialog", "Displays modal GUI dialog window to player: ShowPlayerDialog(playerid, dialogid, style, caption[], info[], button1[], button2[])", PawnItemKind.FUNCTION),
        PawnLanguageItem("TextDrawCreate", "Creates global on-screen text draw: TextDrawCreate(Float:x, Float:y, text[])", PawnItemKind.FUNCTION),
        PawnLanguageItem("TextDrawDestroy", "Destroys global text draw: TextDrawDestroy(Text:text)", PawnItemKind.FUNCTION),
        PawnLanguageItem("TextDrawShowForPlayer", "Displays text draw to player: TextDrawShowForPlayer(playerid, Text:text)", PawnItemKind.FUNCTION),
        PawnLanguageItem("TextDrawHideForPlayer", "Hides text draw from player: TextDrawHideForPlayer(playerid, Text:text)", PawnItemKind.FUNCTION),
        PawnLanguageItem("TextDrawSetString", "Updates text draw string: TextDrawSetString(Text:text, string[])", PawnItemKind.FUNCTION),
        PawnLanguageItem("fopen", "Opens file on disk: fopen(name[], mode)", PawnItemKind.FUNCTION),
        PawnLanguageItem("fclose", "Closes open file handle: fclose(File:handle)", PawnItemKind.FUNCTION),
        PawnLanguageItem("fwrite", "Writes text line to file: fwrite(File:handle, string[])", PawnItemKind.FUNCTION),
        PawnLanguageItem("fread", "Reads text line from file: fread(File:handle, string[], size, bool:pack)", PawnItemKind.FUNCTION),
        PawnLanguageItem("fexist", "Checks if file exists on server disk: fexist(name[])", PawnItemKind.FUNCTION),
        PawnLanguageItem("fremove", "Deletes file from server disk: fremove(name[])", PawnItemKind.FUNCTION)
    )

    private val allItems: List<PawnLanguageItem> by lazy {
        buildList {
            addAll(DIRECTIVES)
            addAll(KEYWORDS)
            addAll(TYPES)
            addAll(CONSTANTS)
            addAll(CALLBACKS)
            addAll(NATIVES)
        }
    }

    private val keywordSet: Set<String> by lazy {
        KEYWORDS.map { it.name }.toSet()
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
    fun isFunction(word: String): Boolean = word in nativeSet || word in callbackSet

    fun getCompletions(prefix: String): List<PawnLanguageItem> {
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
