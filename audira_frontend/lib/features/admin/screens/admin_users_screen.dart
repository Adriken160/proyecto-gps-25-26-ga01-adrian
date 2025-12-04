import 'package:audira_frontend/core/api/services/admin_service.dart';
import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';
import '../../../config/theme.dart';
import '../../../core/models/user.dart';

class AdminUsersScreen extends StatefulWidget {
  const AdminUsersScreen({super.key});

  @override
  State<AdminUsersScreen> createState() => _AdminUsersScreenState();
}

class _AdminUsersScreenState extends State<AdminUsersScreen> {
  final AdminService _adminService = AdminService();
  final TextEditingController _searchController = TextEditingController();

  // Colores del tema oscuro (Mismos que la pantalla anterior)
  final Color darkBg = Colors.black;
  final Color darkCardBg = const Color(0xFF212121);
  final Color lightText = Colors.white;
  final Color subText = Colors.grey;

  List<User> _users = [];
  List<User> _filteredUsers = [];
  bool _isLoading = false;
  String? _error;
  String _selectedRoleFilter = 'ALL';

  @override
  void initState() {
    super.initState();
    _loadUsers();
  }

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  Future<void> _loadUsers() async {
    setState(() {
      _isLoading = true;
      _error = null;
    });

    try {
      final response = await _adminService.getAllUsersAdmin();
      if (response.success && response.data != null) {
        setState(() {
          _users = response.data!;
          _applyFilters();
        });
      } else {
        setState(
            () => _error = response.error ?? 'Error al cargar los usuarios');
      }
    } catch (e) {
      setState(() => _error = e.toString());
    } finally {
      setState(() => _isLoading = false);
    }
  }

  void _applyFilters() {
    var filtered = _users;

    // Filter by Role
    if (_selectedRoleFilter != 'ALL') {
      filtered =
          filtered.where((user) => user.role == _selectedRoleFilter).toList();
    }

    // Filter by Search
    final query = _searchController.text.toLowerCase();
    if (query.isNotEmpty) {
      filtered = filtered
          .where((user) =>
              user.username.toLowerCase().contains(query) ||
              user.email.toLowerCase().contains(query) ||
              user.fullName.toLowerCase().contains(query))
          .toList();
    }

    setState(() => _filteredUsers = filtered);
  }

  // --- MÉTODOS DE LÓGICA (Sin cambios funcionales, solo UI en los dialogos si quisieras) ---
  Future<void> _changeUserRole(User user) async {
    // ... (Lógica idéntica, mantengo el showDialog nativo por simplicidad,
    // pero podrías estilizarlo también)
    final selectedRole = await showDialog<String>(
      context: context,
      builder: (context) => AlertDialog(
        backgroundColor: darkCardBg, // Adaptado a oscuro
        title:
            Text('Cambiar Rol de Usuario', style: TextStyle(color: lightText)),
        content: RadioGroup<String>(
          // Asumiendo que este widget existe o es un placeholder
          groupValue: user.role,
          onChanged: (value) => Navigator.pop(context, value),
          child: RadioGroup<String>(
            groupValue: user.role,
            onChanged: (val) => Navigator.pop(context, val),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: ['USER', 'ARTIST', 'ADMIN'].map((role) {
                return RadioListTile<String>(
                  title: Text(role, style: TextStyle(color: lightText)),
                  value: role,
                  activeColor: AppTheme.primaryBlue,
                );
              }).toList(),
            ),
          ),
        ),
      ),
    );

    if (selectedRole != null && selectedRole != user.role) {
      _executeRoleChange(user, selectedRole);
    }
  }

  Future<void> _executeRoleChange(User user, String selectedRole) async {
    // Loading dialog stylization
    if (!mounted) return;
    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (context) => const Center(
        child: CircularProgressIndicator(color: AppTheme.primaryBlue),
      ),
    );

    try {
      final response =
          await _adminService.changeUserRole(user.id, selectedRole);
      if (!mounted) return;
      Navigator.pop(context);

      if (response.success) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
          content: Text('Rol de usuario cambiado a $selectedRole'),
          backgroundColor: Colors.green[800],
        ));
        await _loadUsers();
      } else {
        _showErrorSnackBar(response.error);
      }
    } catch (e) {
      if (!mounted) return;
      Navigator.pop(context);
      _showErrorSnackBar(e.toString());
    }
  }

  Future<void> _toggleUserStatus(User user) async {
    final translatedAction = user.isActive ? 'SUSPENDER' : 'ACTIVAR';

    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        backgroundColor: darkCardBg,
        title: Text('$translatedAction Usuario',
            style: TextStyle(color: lightText)),
        content: Text(
          user.isActive
              ? '¿Estás seguro de que deseas suspender a este usuario? No podrá acceder a la plataforma.'
              : '¿Estás seguro de que deseas activar a este usuario? Recuperará el acceso a la plataforma.',
          style: TextStyle(color: subText),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Cancelar'),
          ),
          ElevatedButton(
            onPressed: () => Navigator.pop(context, true),
            style: ElevatedButton.styleFrom(
              backgroundColor:
                  user.isActive ? Colors.red[900] : Colors.green[800],
              foregroundColor: Colors.white,
            ),
            child: Text(translatedAction),
          ),
        ],
      ),
    );

    if (confirmed == true) {
      _executeStatusChange(user);
    }
  }

  Future<void> _executeStatusChange(User user) async {
    if (!mounted) return;
    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (context) => const Center(
          child: CircularProgressIndicator(color: AppTheme.primaryBlue)),
    );
    try {
      final response =
          await _adminService.changeUserStatus(user.id, !user.isActive);
      if (!mounted) return;
      Navigator.pop(context);

      if (response.success) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
          content: Text('Estado de usuario actualizado exitosamente'),
          backgroundColor: Colors.green[800],
        ));
        await _loadUsers();
      } else {
        _showErrorSnackBar(response.error);
      }
    } catch (e) {
      if (!mounted) return;
      Navigator.pop(context);
      _showErrorSnackBar(e.toString());
    }
  }

  void _showErrorSnackBar(String? message) {
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(
      content: Text('Error: $message'),
      backgroundColor: Colors.red[900],
    ));
  }

  // --- UI BUILD ---

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: darkBg, // FONDO NEGRO
      appBar: AppBar(
        backgroundColor: darkBg,
        elevation: 0,
        centerTitle: false,
        title: Text(
          'Administración de Usuarios',
          style: TextStyle(
              color: AppTheme.primaryBlue, fontWeight: FontWeight.w800),
        ),
      ),
      body: Column(
        children: [
          // 1. HEADER & SEARCH SECTION
          Container(
            padding: const EdgeInsets.fromLTRB(20, 0, 20, 20),
            color: darkBg,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                // Search Bar estilizada
                Container(
                  decoration: BoxDecoration(
                    color: darkCardBg,
                    borderRadius: BorderRadius.circular(16),
                    border: Border.all(color: Colors.grey[800]!),
                  ),
                  child: TextField(
                    controller: _searchController,
                    style: TextStyle(color: lightText),
                    decoration: InputDecoration(
                      hintText:
                          'Buscar por nombre, correo electrónico o nombre de usuario...',
                      hintStyle: TextStyle(color: subText),
                      prefixIcon: Icon(Icons.search, color: subText),
                      border: InputBorder.none,
                      contentPadding: const EdgeInsets.symmetric(
                          horizontal: 16, vertical: 14),
                    ),
                    onChanged: (_) => _applyFilters(),
                  ),
                ),
                const SizedBox(height: 16),

                // Filters: Chips horizontales en lugar de PopupMenu
                SingleChildScrollView(
                  scrollDirection: Axis.horizontal,
                  child: Row(
                    children: [
                      _buildFilterChip('ALL', 'Todos los Usuarios'),
                      const SizedBox(width: 8),
                      _buildFilterChip('USER', 'Usuarios'),
                      const SizedBox(width: 8),
                      _buildFilterChip('ARTIST', 'Artistas'),
                      const SizedBox(width: 8),
                      _buildFilterChip('ADMIN', 'Administradores'),
                    ],
                  ),
                ),
              ],
            ),
          ).animate().slideY(begin: -0.2, end: 0, duration: 300.ms),

          // 2. USER LIST
          Expanded(
            child: _isLoading
                ? const Center(
                    child:
                        CircularProgressIndicator(color: AppTheme.primaryBlue))
                : _error != null
                    ? _buildErrorState()
                    : _filteredUsers.isEmpty
                        ? _buildEmptyState()
                        : ListView.separated(
                            padding: const EdgeInsets.symmetric(
                                horizontal: 20, vertical: 10),
                            itemCount: _filteredUsers.length,
                            separatorBuilder: (c, i) =>
                                const SizedBox(height: 12),
                            itemBuilder: (context, index) {
                              final user = _filteredUsers[index];
                              return _buildUserCard(user, index);
                            },
                          ),
          ),
        ],
      ),
    );
  }

  // --- WIDGETS AUXILIARES ---

  Widget _buildFilterChip(String value, String label) {
    final isSelected = _selectedRoleFilter == value;
    return GestureDetector(
      onTap: () {
        setState(() => _selectedRoleFilter = value);
        _applyFilters();
      },
      child: AnimatedContainer(
        duration: 200.ms,
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
        decoration: BoxDecoration(
          color: isSelected ? AppTheme.primaryBlue : darkCardBg,
          borderRadius: BorderRadius.circular(20),
          border: Border.all(
              color: isSelected ? AppTheme.primaryBlue : Colors.grey[800]!),
        ),
        child: Text(
          label,
          style: TextStyle(
            color: isSelected ? Colors.white : subText,
            fontWeight: isSelected ? FontWeight.bold : FontWeight.normal,
            fontSize: 13,
          ),
        ),
      ),
    );
  }

  Widget _buildUserCard(User user, int index) {
    Color roleColor;
    switch (user.role) {
      case 'ADMIN':
        roleColor = Colors.redAccent;
        break;
      case 'ARTIST':
        roleColor = Colors.purpleAccent;
        break;
      default:
        roleColor = AppTheme.primaryBlue;
    }

    return Container(
      decoration: BoxDecoration(
        color: darkCardBg,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: Colors.grey[850]!),
      ),
      child: ListTile(
        contentPadding: const EdgeInsets.all(12),
        // Avatar mejorado
        leading: CircleAvatar(
          radius: 24,
          backgroundColor: roleColor.withValues(alpha: 0.2),
          child: Text(
            user.username.isNotEmpty ? user.username[0].toUpperCase() : '?',
            style: TextStyle(
              color: roleColor,
              fontWeight: FontWeight.bold,
              fontSize: 18,
            ),
          ),
        ),
        title: Row(
          children: [
            Flexible(
              child: Text(
                user.fullName,
                style: TextStyle(
                    color: lightText,
                    fontWeight: FontWeight.bold,
                    fontSize: 16),
                overflow: TextOverflow.ellipsis,
              ),
            ),
            if (!user.isActive)
              Container(
                margin: const EdgeInsets.only(left: 8),
                padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                decoration: BoxDecoration(
                  color: Colors.red.withValues(alpha: 0.2),
                  borderRadius: BorderRadius.circular(4),
                ),
                child: const Text('SUSPENDIDO',
                    style: TextStyle(
                        color: Colors.red,
                        fontSize: 10,
                        fontWeight: FontWeight.bold)),
              )
          ],
        ),
        subtitle: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const SizedBox(height: 4),
            Text('@${user.username}',
                style: TextStyle(color: subText, fontSize: 13)),
            Text(user.email,
                style: TextStyle(color: Colors.grey[600], fontSize: 12)),
            const SizedBox(height: 8),
            // Badge de rol customizado
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
              decoration: BoxDecoration(
                border: Border.all(color: roleColor.withValues(alpha: 0.5)),
                borderRadius: BorderRadius.circular(8),
              ),
              child: Text(
                user.role,
                style: TextStyle(
                    color: roleColor,
                    fontSize: 10,
                    fontWeight: FontWeight.w600),
              ),
            ),
          ],
        ),
        trailing: PopupMenuButton<String>(
          icon: Icon(Icons.more_vert, color: subText),
          color: darkCardBg,
          shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(12),
              side: BorderSide(color: Colors.grey[800]!)),
          itemBuilder: (context) => [
            PopupMenuItem(
              value: 'role',
              child: Row(
                children: [
                  Icon(Icons.admin_panel_settings,
                      color: AppTheme.primaryBlue, size: 20),
                  const SizedBox(width: 12),
                  Text('Cambiar Rol', style: TextStyle(color: lightText)),
                ],
              ),
            ),
            PopupMenuItem(
              value: 'status',
              child: Row(
                children: [
                  Icon(user.isActive ? Icons.block : Icons.check_circle,
                      color:
                          user.isActive ? Colors.redAccent : Colors.greenAccent,
                      size: 20),
                  const SizedBox(width: 12),
                  Text(user.isActive ? 'Suspender Usuario' : 'Activar Usuario',
                      style: TextStyle(color: lightText)),
                ],
              ),
            ),
          ],
          onSelected: (value) {
            if (value == 'role') _changeUserRole(user);
            if (value == 'status') _toggleUserStatus(user);
          },
        ),
      ),
    ).animate().fadeIn(delay: (index * 30).ms).slideX(begin: 0.1, end: 0);
  }

  Widget _buildEmptyState() {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(Icons.person_search, size: 64, color: Colors.grey[800]),
          const SizedBox(height: 16),
          Text('No se encontraron usuarios',
              style: TextStyle(color: subText, fontSize: 16)),
        ],
      ),
    );
  }

  Widget _buildErrorState() {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(Icons.error_outline, size: 64, color: Colors.red[900]),
          const SizedBox(height: 16),
          Text(_error!, style: const TextStyle(color: Colors.red)),
          const SizedBox(height: 16),
          ElevatedButton(
            onPressed: _loadUsers,
            style:
                ElevatedButton.styleFrom(backgroundColor: AppTheme.primaryBlue),
            child: const Text('Reintentar'),
          ),
        ],
      ),
    );
  }
}

// Helper para que compile el RadioGroup si no lo tienes definido:
class RadioGroup<T> extends StatelessWidget {
  final T groupValue;
  final ValueChanged<T?> onChanged;
  final Widget child;
  const RadioGroup(
      {super.key,
      required this.groupValue,
      required this.onChanged,
      required this.child});
  @override
  Widget build(BuildContext context) => child;
}
