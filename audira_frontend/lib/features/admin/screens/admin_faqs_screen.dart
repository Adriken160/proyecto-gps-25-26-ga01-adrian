import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';
import '../../../config/theme.dart';
import '../../../core/models/faq.dart';
import '../../../core/api/services/faq_service.dart';

class AdminFaqsScreen extends StatefulWidget {
  const AdminFaqsScreen({super.key});

  @override
  State<AdminFaqsScreen> createState() => _AdminFaqsScreenState();
}

class _AdminFaqsScreenState extends State<AdminFaqsScreen> {
  final FaqService _faqService = FaqService();

  // --- Colores del Tema Oscuro ---
  final Color darkBg = Colors.black;
  final Color darkCardBg = const Color(0xFF212121);
  final Color lightText = Colors.white;
  final Color subText = Colors.grey;

  List<FAQ> _faqs = [];
  bool _isLoading = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _loadFaqs();
  }

  Future<void> _loadFaqs() async {
    setState(() {
      _isLoading = true;
      _error = null;
    });

    try {
      final response = await _faqService.getAllFaqs();
      if (response.success && response.data != null) {
        setState(() => _faqs = response.data!);
      } else {
        setState(() => _error =
            response.error ?? 'Error al cargar las Preguntas Frecuentes');
      }
    } catch (e) {
      setState(() => _error = e.toString());
    } finally {
      setState(() => _isLoading = false);
    }
  }

  Future<void> _deleteFaq(int id) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        backgroundColor: darkCardBg,
        title: Text('Eliminar Pregunta Frecuente',
            style: TextStyle(color: lightText)),
        content: Text(
            '¿Estás seguro de que quieres eliminar esta Pregunta Frecuente?',
            style: TextStyle(color: subText)),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Cancelar'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(context, true),
            style: TextButton.styleFrom(foregroundColor: Colors.redAccent),
            child: const Text('Eliminar'),
          ),
        ],
      ),
    );

    if (confirmed == true) {
      final currentContext = context;
      try {
        final response = await _faqService.deleteFaq(id);
        if (!currentContext.mounted) return;
        if (response.success) {
          _showSnack('Pregunta Frecuente eliminada exitosamente');
          _loadFaqs();
        } else {
          _showSnack(
              response.error ?? 'Error al eliminar la Pregunta Frecuente',
              isError: true);
        }
      } catch (e) {
        if (!currentContext.mounted) return;
        _showSnack('Error: $e', isError: true);
      }
    }
  }

  void _showSnack(String msg, {bool isError = false}) {
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(
      content: Text(msg),
      backgroundColor: isError ? Colors.red[900] : Colors.green[800],
    ));
  }

  @override
  Widget build(BuildContext context) {
    // Calcular métricas simples
    final totalViews = _faqs.fold(0, (sum, faq) => sum + (faq.viewCount));

    return Scaffold(
      backgroundColor: darkBg,
      appBar: AppBar(
        title: const Text('Administrar Preguntas Frecuentes',
            style: TextStyle(
                color: AppTheme.primaryBlue, fontWeight: FontWeight.w800)),
        backgroundColor: darkBg,
        elevation: 0,
        centerTitle: false,
        actions: [
          Container(
            margin: const EdgeInsets.only(right: 16),
            decoration: BoxDecoration(
              color: AppTheme.primaryBlue.withValues(alpha: 0.1),
              borderRadius: BorderRadius.circular(12),
            ),
            child: IconButton(
              icon: Icon(Icons.add, color: AppTheme.primaryBlue),
              onPressed: () => _showFaqForm(null),
              tooltip: 'Añadir Pregunta Frecuente',
            ),
          ),
        ],
      ),
      body: Column(
        children: [
          // 1. HEADER STATS
          Container(
            padding: const EdgeInsets.fromLTRB(20, 10, 20, 20),
            color: darkBg,
            child: Row(
              children: [
                Expanded(
                  child: _buildMiniStat('Preguntas Totales',
                      _faqs.length.toString(), Icons.quiz, Colors.purpleAccent),
                ),
                const SizedBox(width: 16),
                Expanded(
                  child: _buildMiniStat('Vistas Totales', totalViews.toString(),
                      Icons.visibility, Colors.orangeAccent),
                ),
              ],
            ),
          ).animate().slideY(begin: -0.2, end: 0, duration: 300.ms),

          // 2. LISTA DE FAQs
          Expanded(
            child: _isLoading
                ? const Center(
                    child:
                        CircularProgressIndicator(color: AppTheme.primaryBlue))
                : _error != null
                    ? _buildErrorState()
                    : _faqs.isEmpty
                        ? _buildEmptyState()
                        : ListView.builder(
                            padding: const EdgeInsets.fromLTRB(20, 0, 20, 80),
                            itemCount: _faqs.length,
                            itemBuilder: (context, index) {
                              return _buildFaqCard(_faqs[index], index);
                            },
                          ),
          ),
        ],
      ),
    );
  }

  // --- WIDGETS AUXILIARES ---

  Widget _buildMiniStat(
      String label, String value, IconData icon, Color color) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: darkCardBg,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: Colors.grey[850]!),
      ),
      child: Row(
        children: [
          Container(
            padding: const EdgeInsets.all(10),
            decoration: BoxDecoration(
              color: color.withValues(alpha: 0.1),
              borderRadius: BorderRadius.circular(10),
            ),
            child: Icon(icon, color: color, size: 20),
          ),
          const SizedBox(width: 12),
          // CAMBIO AQUÍ: Envuelto en Expanded para evitar overflow
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  value,
                  style: TextStyle(
                      color: lightText,
                      fontWeight: FontWeight.bold,
                      fontSize: 18),
                  overflow: TextOverflow.ellipsis, // Cortar si es muy largo
                  maxLines: 1,
                ),
                Text(
                  label,
                  style: TextStyle(color: subText, fontSize: 11),
                  overflow: TextOverflow.ellipsis, // Cortar si es muy largo
                  maxLines: 1,
                ),
              ],
            ),
          )
        ],
      ),
    );
  }

  Widget _buildFaqCard(FAQ faq, int index) {
    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      decoration: BoxDecoration(
        color: darkCardBg,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: Colors.grey[850]!),
      ),
      child: Theme(
        // Quitamos los bordes por defecto del ExpansionTile
        data: Theme.of(context).copyWith(dividerColor: Colors.transparent),
        child: ExpansionTile(
          tilePadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
          childrenPadding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
          leading: Container(
            padding: const EdgeInsets.all(8),
            decoration: BoxDecoration(
              color: AppTheme.primaryBlue.withValues(alpha: 0.1),
              shape: BoxShape.circle,
            ),
            child: Text(
              'P${index + 1}', // Q -> P de Question (Pregunta)
              style: const TextStyle(
                  color: AppTheme.primaryBlue,
                  fontWeight: FontWeight.bold,
                  fontSize: 12),
            ),
          ),
          title: Text(
            faq.question,
            style: TextStyle(
                color: lightText, fontWeight: FontWeight.bold, fontSize: 15),
          ),
          subtitle: Padding(
            padding: const EdgeInsets.only(top: 6),
            child: Row(
              children: [
                Container(
                  padding:
                      const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                  decoration: BoxDecoration(
                    color: Colors.grey[800],
                    borderRadius: BorderRadius.circular(4),
                  ),
                  child: Text(
                    faq.category.toUpperCase(),
                    style: TextStyle(
                        color: Colors.grey[400],
                        fontSize: 10,
                        fontWeight: FontWeight.w600),
                  ),
                ),
                const SizedBox(width: 12),
                Icon(Icons.visibility, size: 12, color: subText),
                const SizedBox(width: 4),
                Text('${faq.viewCount}',
                    style: TextStyle(color: subText, fontSize: 12)),
              ],
            ),
          ),
          children: [
            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: Colors.black.withValues(alpha: 0.3),
                borderRadius: BorderRadius.circular(12),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Icon(Icons.chat_bubble_outline,
                          size: 16, color: AppTheme.primaryBlue),
                      const SizedBox(width: 8),
                      Text("RESPUESTA",
                          style: TextStyle(
                              color: AppTheme.primaryBlue,
                              fontSize: 11,
                              fontWeight: FontWeight.bold)),
                    ],
                  ),
                  const SizedBox(height: 8),
                  Text(
                    faq.answer,
                    style: TextStyle(color: Colors.grey[300], height: 1.5),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 12),
            Row(
              mainAxisAlignment: MainAxisAlignment.end,
              children: [
                TextButton.icon(
                  onPressed: () => _showFaqForm(faq),
                  icon: const Icon(Icons.edit, size: 16),
                  label: const Text('Editar'),
                  style: TextButton.styleFrom(foregroundColor: subText),
                ),
                TextButton.icon(
                  onPressed: () => _deleteFaq(faq.id),
                  icon: const Icon(Icons.delete, size: 16),
                  label: const Text('Eliminar'),
                  style:
                      TextButton.styleFrom(foregroundColor: Colors.redAccent),
                ),
              ],
            )
          ],
        ),
      ),
    ).animate().fadeIn(delay: (index * 50).ms).slideX(begin: 0.1, end: 0);
  }

  Widget _buildEmptyState() {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(Icons.help_outline, size: 64, color: Colors.grey[800]),
          const SizedBox(height: 16),
          Text('No se encontraron Preguntas Frecuentes',
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
            onPressed: _loadFaqs,
            style:
                ElevatedButton.styleFrom(backgroundColor: AppTheme.primaryBlue),
            child: const Text('Reintentar'),
          ),
        ],
      ),
    );
  }

  // --- FORMULARIOS (Adaptados a Dark Mode) ---

  void _showFaqForm(FAQ? faq) {
    final currentContext = context;
    final isEditing = faq != null;
    final questionController = TextEditingController(text: faq?.question ?? '');
    final answerController = TextEditingController(text: faq?.answer ?? '');
    final categoryController = TextEditingController(text: faq?.category ?? '');

    // Helper para campos de texto
    Widget buildTextField(String label, TextEditingController ctrl,
        {int lines = 1}) {
      return TextField(
        controller: ctrl,
        maxLines: lines,
        style: TextStyle(color: lightText),
        decoration: InputDecoration(
          labelText: label,
          labelStyle: TextStyle(color: subText),
          alignLabelWithHint: true,
          enabledBorder: OutlineInputBorder(
              borderSide: BorderSide(color: Colors.grey[700]!)),
          focusedBorder: OutlineInputBorder(
              borderSide: BorderSide(color: AppTheme.primaryBlue)),
          filled: true,
          fillColor: Colors.black.withValues(alpha: 0.3),
        ),
      );
    }

    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        backgroundColor: darkCardBg,
        title: Text(
            isEditing
                ? 'Editar Pregunta Frecuente'
                : 'Añadir Nueva Pregunta Frecuente',
            style: TextStyle(color: lightText)),
        content: SingleChildScrollView(
          child: SizedBox(
            width: 400, // Anchura fija para que se vea bien
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                buildTextField('Pregunta', questionController, lines: 2),
                const SizedBox(height: 16),
                buildTextField('Respuesta', answerController, lines: 5),
                const SizedBox(height: 16),
                buildTextField('Categoría', categoryController),
              ],
            ),
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Cancelar'),
          ),
          ElevatedButton(
            style: ElevatedButton.styleFrom(
                backgroundColor: AppTheme.primaryBlue,
                foregroundColor: Colors.white),
            onPressed: () async {
              final question = questionController.text.trim();
              final answer = answerController.text.trim();
              final category = categoryController.text.trim();

              if (question.isEmpty || answer.isEmpty) {
                _showSnack('La pregunta y la respuesta son obligatorias',
                    isError: true);
                return;
              }

              Navigator.pop(context);

              try {
                if (isEditing) {
                  await _faqService.updateFaq(
                    id: faq.id,
                    question: question,
                    answer: answer,
                    category: category,
                  );
                } else {
                  await _faqService.createFaq(
                    question: question,
                    answer: answer,
                    category: category,
                  );
                }
                if (!currentContext.mounted) return;
                _showSnack(isEditing
                    ? 'Pregunta Frecuente actualizada exitosamente'
                    : 'Pregunta Frecuente creada exitosamente');
                _loadFaqs();
              } catch (e) {
                if (!currentContext.mounted) return;
                _showSnack('Error: $e', isError: true);
              }
            },
            child: Text(isEditing ? 'Actualizar' : 'Crear'),
          ),
        ],
      ),
    );
  }
}
