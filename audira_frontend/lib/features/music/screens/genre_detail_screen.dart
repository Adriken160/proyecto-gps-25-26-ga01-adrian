import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';

// Imports de tu proyecto
import 'package:audira_frontend/config/theme.dart';
import 'package:audira_frontend/core/api/services/music_service.dart';
import 'package:audira_frontend/core/models/album.dart';
import 'package:audira_frontend/core/models/genre.dart';
import 'package:audira_frontend/core/models/song.dart';
import 'package:audira_frontend/features/common/widgets/album_list_item.dart';
import 'package:audira_frontend/features/common/widgets/song_list_item.dart';
import 'package:audira_frontend/features/common/widgets/mini_player.dart'; // Importante para el player

class GenreDetailScreen extends StatefulWidget {
  final int genreId;

  const GenreDetailScreen({super.key, required this.genreId});

  @override
  State<GenreDetailScreen> createState() => _GenreDetailScreenState();
}

class _GenreDetailScreenState extends State<GenreDetailScreen>
    with SingleTickerProviderStateMixin {
  final MusicService _musicService = MusicService();

  Genre? _genre;
  List<Song> _songs = [];
  List<Album> _albums = [];

  bool _isLoading = true;
  String? _error;

  late TabController _tabController;
  final ScrollController _scrollController = ScrollController();

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 2, vsync: this);
    _loadGenreDetails();
  }

  @override
  void dispose() {
    _tabController.dispose();
    _scrollController.dispose();
    super.dispose();
  }

  Future<void> _loadGenreDetails() async {
    setState(() {
      _isLoading = true;
      _error = null;
    });

    try {
      final genreResponse = await _musicService.getGenreById(widget.genreId);
      if (genreResponse.success && genreResponse.data != null) {
        _genre = genreResponse.data;

        final songsResponse =
            await _musicService.getSongsByGenre(widget.genreId);
        if (songsResponse.success && songsResponse.data != null) {
          _songs = songsResponse.data!;
        }

        final albumsResponse =
            await _musicService.getAlbumsByGenre(widget.genreId);
        if (albumsResponse.success && albumsResponse.data != null) {
          _albums = albumsResponse.data!;
        }
      } else {
        _error = genreResponse.error ?? 'Error al cargar el género';
      }
    } catch (e) {
      _error = e.toString();
    } finally {
      if (mounted) setState(() => _isLoading = false);
    }
  }

  // Helper para convertir el color hex del género a Color
  Color _getGenreColor() {
    if (_genre?.color == null) return AppTheme.primaryBlue;
    try {
      return Color(int.parse(_genre!.color!.replaceFirst('#', '0xff')));
    } catch (e) {
      return AppTheme.primaryBlue;
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_isLoading) {
      return const Scaffold(
        backgroundColor: Color(0xFF121212),
        body: Center(
            child: CircularProgressIndicator(color: AppTheme.primaryBlue)),
      );
    }

    if (_error != null || _genre == null) {
      return Scaffold(
        backgroundColor: const Color(0xFF121212),
        appBar: AppBar(backgroundColor: Colors.transparent),
        body: Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              const Icon(Icons.error_outline, size: 60, color: Colors.white24),
              const SizedBox(height: 16),
              Text(_error ?? 'Género no encontrado',
                  style: const TextStyle(color: Colors.white)),
            ],
          ),
        ),
      );
    }

    final genreColor = _getGenreColor();

    return Scaffold(
      backgroundColor: const Color(0xFF121212),
      body: Stack(
        children: [
          NestedScrollView(
            controller: _scrollController,
            headerSliverBuilder: (context, innerBoxIsScrolled) {
              return [
                _buildSliverAppBar(genreColor),
                SliverPersistentHeader(
                  delegate: _SliverAppBarDelegate(_buildTabBar()),
                  pinned: true,
                ),
              ];
            },
            body: TabBarView(
              controller: _tabController,
              children: [
                _buildSongsTab(),
                _buildAlbumsTab(),
              ],
            ),
          ),

          // Mini Player
          const Positioned(
            bottom: 0,
            left: 0,
            right: 0,
            child: MiniPlayer(),
          ),
        ],
      ),
    );
  }

  // --- CABECERA ---

  Widget _buildSliverAppBar(Color genreColor) {
    return SliverAppBar(
      backgroundColor: const Color(0xFF121212),
      expandedHeight: 280.0,
      pinned: true,
      elevation: 0,
      leading: IconButton(
        icon: Container(
          padding: const EdgeInsets.all(8),
          decoration: const BoxDecoration(
              color: Colors.black26, shape: BoxShape.circle),
          child: const Icon(Icons.arrow_back_ios_new_rounded,
              size: 20, color: Colors.white),
        ),
        onPressed: () => Navigator.pop(context),
      ),
      flexibleSpace: FlexibleSpaceBar(
        background: Stack(
          fit: StackFit.expand,
          children: [
            // 1. DEGRADADO DE COLOR
            Container(
              decoration: BoxDecoration(
                gradient: LinearGradient(
                  begin: Alignment.topCenter,
                  end: Alignment.bottomCenter,
                  colors: [
                    genreColor,
                    const Color(0xFF121212),
                  ],
                  stops: const [0.0, 0.9],
                ),
              ),
            ),

            // 2. PATRÓN DECORATIVO (OPCIONAL)
            Positioned(
              right: -50,
              top: -50,
              child: Icon(
                Icons.music_note_rounded,
                size: 300,
                color: Colors.white.withValues(alpha: 0.05),
              ),
            ),

            // 3. CONTENIDO DEL GÉNERO
            Align(
              alignment: Alignment.bottomLeft,
              child: Padding(
                padding: const EdgeInsets.all(24.0),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    // Icono del género
                    Container(
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(
                        color: Colors.white.withValues(alpha: 0.1),
                        borderRadius: BorderRadius.circular(12),
                        boxShadow: [
                          BoxShadow(
                            color: Colors.black.withValues(alpha: 0.2),
                            blurRadius: 10,
                            offset: const Offset(0, 5),
                          )
                        ],
                      ),
                      child: Text(
                        _genre!.icon ?? '🎵',
                        style: const TextStyle(fontSize: 40),
                      ),
                    )
                        .animate()
                        .scale(duration: 500.ms, curve: Curves.elasticOut),

                    const SizedBox(height: 16),

                    Text(
                      _genre!.name,
                      style: const TextStyle(
                        color: Colors.white,
                        fontSize: 32,
                        fontWeight: FontWeight.w800,
                        letterSpacing: -1,
                      ),
                    ).animate().fadeIn().slideX(begin: -0.2, end: 0),

                    if (_genre!.description != null &&
                        _genre!.description!.isNotEmpty) ...[
                      const SizedBox(height: 8),
                      Text(
                        _genre!.description!,
                        style: TextStyle(
                          color: Colors.white.withValues(alpha: 0.7),
                          fontSize: 14,
                          height: 1.4,
                        ),
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                      ),
                    ],
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  // --- TABS ---

  Widget _buildTabBar() {
    return Container(
      color: const Color(0xFF121212), // Fondo sólido para sticky effect
      child: TabBar(
        controller: _tabController,
        indicatorColor: _getGenreColor(), // Indicador del color del género
        indicatorWeight: 3,
        labelColor: Colors.white,
        unselectedLabelColor: Colors.white54,
        labelStyle: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
        tabs: [
          Tab(text: 'Canciones (${_songs.length})'),
          Tab(text: 'Álbumes (${_albums.length})'),
        ],
      ),
    );
  }

  Widget _buildSongsTab() {
    if (_songs.isEmpty) {
      return _buildEmptyState('No hay canciones en este género');
    }

    return ListView.builder(
      // Padding inferior para el MiniPlayer
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 100),
      itemCount: _songs.length,
      itemBuilder: (context, index) {
        final song = _songs[index];
        return SongListItem(
          song: song,
          onTap: () {
            Navigator.pushNamed(context, '/song', arguments: song.id);
          },
        ).animate().fadeIn(duration: 300.ms, delay: (30 * index).ms);
      },
    );
  }

  Widget _buildAlbumsTab() {
    if (_albums.isEmpty) {
      return _buildEmptyState('No hay álbumes en este género');
    }

    return ListView.builder(
      // Padding inferior para el MiniPlayer
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 100),
      itemCount: _albums.length,
      itemBuilder: (context, index) {
        final album = _albums[index];
        return Padding(
          padding: const EdgeInsets.only(bottom: 8.0),
          child: AlbumListItem(
            album: album,
            onTap: () {
              Navigator.pushNamed(context, '/album', arguments: album.id);
            },
          ),
        ).animate().fadeIn(duration: 300.ms, delay: (50 * index).ms);
      },
    );
  }

  Widget _buildEmptyState(String message) {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(Icons.album_outlined,
              size: 60, color: Colors.white.withValues(alpha: 0.1)),
          const SizedBox(height: 16),
          Text(
            message,
            style: TextStyle(color: Colors.white.withValues(alpha: 0.5)),
          ),
        ],
      ),
    );
  }
}

// --- DELEGADO PARA EL HEADER PEGAJOSO ---
class _SliverAppBarDelegate extends SliverPersistentHeaderDelegate {
  final Widget _tabBar;

  _SliverAppBarDelegate(this._tabBar);

  @override
  double get minExtent => 48.0;
  @override
  double get maxExtent => 48.0;

  @override
  Widget build(
      BuildContext context, double shrinkOffset, bool overlapsContent) {
    return _tabBar;
  }

  @override
  bool shouldRebuild(_SliverAppBarDelegate oldDelegate) {
    return false;
  }
}
